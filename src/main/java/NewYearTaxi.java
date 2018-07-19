import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class NewYearTaxi {
    private static final String FILENAME = "2/input3.txt";
    private static final boolean DEBUG_PRINT = true;
    private static final Pattern SPACE_PATTERN = Pattern.compile(" ");

    private static final Consumer<String> debug = (s -> {
        //Не нормальный логгер конечно.. строку все равно прийдется "создать"
        //и потратить на это время, даже если мы ее печатать не собираемся
        //Если только он сам там как-нибудь не оптимизирует как "мертвый" кусок кода ибо static final ?
        if (DEBUG_PRINT)
            System.out.println(s);
    });

    //Класс точка, для хранения позиции
    static class Point {
        int x;
        int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return x == point.x &&
                    y == point.y;
        }

        @Override
        public int hashCode() {

            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "Point{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }

        public int travelTimeTo(Point p) {
            return Math.abs(x - p.x) + Math.abs(y - p.y);
        }
    }

    //Заказ
    static class Order {

        //ID, Начало, Конец
        int id;
        Point begin;
        Point end;

        //Его стоимость (время выполнения begin -> end без учета того, что водитель еще должен до begin доехать)
        int cost;

        //Преподсчитанные значения, сколько этот заказ будет выполнять тот или иной водитель
        //Наверное не очень здесь это хранить, и лучше было бы хранить где-то "во вне", но по бырику сделал так
        TreeMap<Integer, Set<Taxi>> costsMap;

        public Order(int id, Point begin, Point end) {
            this.id = id;
            this.begin = begin;
            this.end = end;
            //Чтобы каждый раз не считать одно и тоже
            this.cost = begin.travelTimeTo(end);
            costsMap = new TreeMap<>();
        }

        public int getCostForTaxi(Taxi taxi) {
            return taxi.p.travelTimeTo(begin) + cost;
        }

        //Для правильной работы алгоритма нужен преподсчет значений для всех таксистов и их сохранение
        //Этот метод расчитывает стоимость и сохраняет ее
        public void fillTimeCostForTaxi(Taxi taxi) {
            int taxiCost = getCostForTaxi(taxi);
            //Сохранили "рейтинг" стоимости этого заказа для водителей
            Set<Taxi> taxisWithThisCost = costsMap.getOrDefault(taxiCost, new HashSet<>());
            taxisWithThisCost.add(taxi);
            costsMap.put(taxiCost, taxisWithThisCost);
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Order order = (Order) o;
            return id == order.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Order{" +
                    "id=" + id +
                    ", begin=" + begin +
                    ", end=" + end +
                    '}';
        }
    }

    //Дескриптор такси, хранит идентификатор и начальное положение
    static class Taxi implements Comparable<Taxi> {
        int id;
        Point p;

        public Taxi(int id, Point p) {
            this.id = id;
            this.p = p;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Taxi taxi = (Taxi) o;
            return id == taxi.id;
        }

        @Override
        public int hashCode() {

            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Taxi{" +
                    "id=" + id +
                    ", p=" + p +
                    '}';
        }

        //Чтобы можно было засунуть в TreeMap и итерировать в порядке возрастания ID(для вывода)
        @Override
        public int compareTo(Taxi o) {
            return Integer.compare(id, o.id);
        }
    }

    //Класс дескриптор самого "плохого" (по времени) заказа
    //Хранит водителя который уйдет домой последним, его заказ, и время когда он закончит
    static class WorstOrderInfo implements Comparable<WorstOrderInfo> {

        private Taxi taxi;
        private Order order;
        private int finishTime;

        @Override
        public int compareTo(WorstOrderInfo o) {
            //Хотим получать сначала большие значения, ибо это же время, чем меньше тем лучше
            //Вообще говоря это legacy уже, наверное можно убрать, над внимательней посмотреть
            // (раньше пытался в priority queue засовывать такие объекты)
            return -Integer.compare(finishTime, o.finishTime);
        }

        public WorstOrderInfo(Taxi taxi, Order order, int finishTime) {
            this.taxi = taxi;
            this.order = order;
            this.finishTime = finishTime;
        }

        @Override
        public String toString() {
            return "WorstOrderInfo{" +
                    "taxi=" + taxi +
                    ", order=" + order +
                    ", finishTime=" + finishTime +
                    '}';
        }
    }

    //Это класс - основная рабочая лошадка, ответит нам на вопрос, как собственно распределить заказы
    static class OrderStrategy {

        //Имеет доступ к всем таксистам и заказам
        private Map<Integer, Taxi> taxiMap;
        private Map<Integer, Order> orderMap;
        //Это собственно стратегия распределения
        //Ключ ID такси, значение ID заказа
        private Map<Taxi, Order> strategy;

        //Индекс, ключ - время ухода домой, значение - набор таксистов которые завершат в это время свои поездки
        //Это необходимо для того, чтобы найти водителя, который удет последним, и попытаться улучшить его ситуацию
        private TreeMap<Integer, Set<Taxi>> timetoHome;

        public OrderStrategy(Map<Integer, Taxi> taxiIndex, Map<Integer, Order> orderIndex, int carCount) {
            this.taxiMap = taxiIndex;
            this.orderMap = orderIndex;
            strategy = new TreeMap<>();
            timetoHome = new TreeMap<>();

            //Первым делом, сделаем преподсчет стоимости заказов для всех такси
            for (Order o : orderIndex.values()) {
                for (Taxi t : taxiIndex.values()) {
                    o.fillTimeCostForTaxi(t);
                }
            }

            //
            //По умолчанию наполняем дефолтным распределением
            for (int i = 1; i <= carCount; i++) {
                put(taxiMap.get(i), orderMap.get(i));
            }
        }


        //Меняем заказы между двумя таксистами
        //Сложность еще в том, что помимо заказов, нужно обновить время убытия домой водителей
        //И я так и не придумал никакой нормальной структуры данных для этого
        //(из очереди нужно постоянно удалять элемент - это дорого)
        public void swapOrdersForTaxi(Taxi t1, Taxi t2) {
            //Это их старые заказы
            Order o1Order = strategy.get(t1);
            Order o2Order = strategy.get(t2);

            //Удаляем старое время убытия домой
            int oldO1TimeToHome = o1Order.getCostForTaxi(t1);
            int oldO2TimeToHome = o2Order.getCostForTaxi(t2);

            Set<Taxi> o1TimeTaxis = timetoHome.get(oldO1TimeToHome);
            Set<Taxi> o2TimeTaxis = timetoHome.get(oldO2TimeToHome);
            o1TimeTaxis.remove(t2);
            o2TimeTaxis.remove(t1);

            //Вычищаем, чтобы далее быть увереным в том, что сет не пуст
            //Иначе возможны случаи что мы достали элемент по ключу а там пустой SET, и чего делать?
            //Следующий элемент искать? Цикл писать? Проще удалить.
            if (o1TimeTaxis.isEmpty())
                timetoHome.remove(oldO1TimeToHome);
            if (o2TimeTaxis.isEmpty())
                timetoHome.remove(oldO2TimeToHome);

            //Добавляем в стратегию, заодно там обновится время ухода
            put(t1, o2Order);
            put(t2, o1Order);
        }


        public void put(Taxi t, Order o) {

            //Добавляет в результат и обновляет информацию о уходе водителей домой
            int costForTaxi = o.getCostForTaxi(t);
            Set<Taxi> taxisToHome = timetoHome.getOrDefault(costForTaxi, new TreeSet<>());
            taxisToHome.add(t);
            timetoHome.put(costForTaxi, taxisToHome);

            strategy.put(t, o);
        }

        //Находим водителя, котрый уходит последним, при текущей стратегии распределения
        private WorstOrderInfo findWorstOrder() {
            Map.Entry<Integer, Set<Taxi>> worstTaxi = timetoHome.lastEntry();
            Taxi wt = worstTaxi.getValue().iterator().next();
            Order wo = strategy.get(wt);
            return new WorstOrderInfo(wt, wo, wo.getCostForTaxi(wt));
        }


        //Этот метод, пытается улучшить время ухода самого последнего водителя - пытается найти того, кто согласен поменяться
        //Согласен в нашей терминологии - это значит он поменялся, и максимальное время ухода уменьшилось
        //(Водители работают по принципу один за всех и все за одного, а не каждый сам за себя :) )
        //True если удалось улучшить ситуацию
        public boolean trySwapForWorstOrder() {
            //У нас есть самое худшее время
            //и самый худший водитель(и заказ соответственно) - попробуем ему улучшить ситуацию - давай найдем того
            //кто готов с ним поменяться, и при этом его время ухода не станет хуже

            //Мы будем искать тех, кто может выполнить худший заказ лучше, и при этом не испортит общую картину
            //(Т.е. последний водитель уйдет раньше)

            WorstOrderInfo worstOrderInfo = findWorstOrder();
            int worstTime = worstOrderInfo.finishTime;
            Order worstOrder = worstOrderInfo.order;
            Taxi worstTaxi = worstOrderInfo.taxi;

            //Кандидатов много, но чтобы не делать полный перебор, мы используем преподсчитанные значения в заказе
            //Стратегии поиска могут быть разные, я здесь начинаю с ЛУЧШЕГО кандидата, который быстрее всего выполнит этот заказ
            //(Почему то чутье подсказывает что нужно наоборот, но это не факт)

            int loopFinishTime = 0; //Будем искать ближайший ключ к этому значению в преподсчитанной map с временем
            debug.accept("Worst order is: " + worstOrderInfo);

            Map.Entry<Integer, Set<Taxi>> entry;
            //Перем первого(с начала), кто сможет выполнить БЫСТРЕЕ
            //Цикл остановится тогда, когда мы посмотрим всех кандидатов для этого заказа, и выяснится что от смены лучше не стало
            while ((entry = worstOrder.costsMap.higherEntry(loopFinishTime)) != null) {
                //Нашли другие такси, которые могут поменяться с ним(может быть несколько)
                Set<Taxi> swapTaxiValue = entry.getValue();
                debug.accept("Seems this order can be done better by taxis: " + swapTaxiValue + "with cost " + entry.getKey());

                //Итерируем
                for (Taxi t : swapTaxiValue) {
                    //Сами с собой не меняемся -- нет смысла
                    if (t.id == worstTaxi.id)
                        continue;

                    //Тут вопрос еще - какого кандидата выбрать? Выбираем первого попавшегося

                    //Смотрим а какой заказ он делал раньше в нашей текущей схеме распределения?
                    Order swapTaxiOrder = strategy.get(t);

                    //Допустим поменяемся с ним? Когда теперь уйдет домой этот водитель?(который выполнил замену)
                    int swapTaxiHomeTime = worstOrder.getCostForTaxi(t);
                    //А когда уйдет водитель, который раньше уходил последним?
                    int worstTaxiNewHomeTime = swapTaxiOrder.getCostForTaxi(worstTaxi);
                    //Все остальные таксисты остались при своих заказах, стало быть можем прикинуть станет ли лучше после смены?

                    int newHomeTime = Math.max(worstTaxiNewHomeTime, swapTaxiHomeTime);

                    //Удалось улучшить время, если время ухода последнего стало меньше
                    if (newHomeTime < worstTime) {
                        //Получается, что после смены заказов, последний водитель уйдет раньше
                        //Меняемся заказами
                        swapOrdersForTaxi(worstTaxi, t);
                        debug.accept("We swapped " + swapTaxiValue + " and " + worstTaxi + " new home time: " + newHomeTime);
                        return true;
                    }
                    debug.accept("Taxi " + t + " is not match for swap, due newHomeTime: " + newHomeTime);
                }
                debug.accept("Unable to find better solution for higher key of " + loopFinishTime + " -> decrease key and try again");
                //не удается улучшить, посмотрим следующего кандидата для этого заказа -- мб у него лучше получится?

                //Значит на следующей итерации, нам дадут СЛЕДУЮЩИЙ ключ, этот мы уже просмотрели
                loopFinishTime = entry.getKey();
            }
            //Просмотрели всех кандидатов - не судьба, этот заказ никто быстрее выполнить не сможет
            return false;
        }

        @Override
        public String toString() {
            return "OrderStrategy{" +
                    "strategy=" + strategy + "}";
        }

    }

    public static void main(String[] args) throws IOException {
        long before = System.currentTimeMillis();
        File f = new File(FILENAME);
        if (f.exists()) {
            //Разбираем файл
            List<String> lines = Files.readAllLines(f.toPath());
            int carCount = Integer.parseInt(lines.get(0));
            //Составляем справочник таксистов и заказов
            Map<Integer, Taxi> taxiMap = new HashMap<>(carCount);
            Map<Integer, Order> orderMap = new HashMap<>(carCount);
            for (int i = 1; i < lines.size(); i++) {
                String s = lines.get(i);
                if (i < carCount + 1) {
                    Taxi taxi = parseTaxiPositionString(i, s);
                    taxiMap.put(i, taxi);
                } else {
                    int orderId = i - (carCount);
                    Order o = parseOrderString(orderId, s);
                    orderMap.put(orderId, o);
                }
            }


            debug.accept("TaxiMap: " + taxiMap);
            debug.accept("OrderMap: " + orderMap);

            OrderStrategy os = new OrderStrategy(taxiMap, orderMap, carCount);
            //Запускаем алгоритм, пытаемся улучшить ситуацию
            while (os.trySwapForWorstOrder()) {
                //Да ниче не делаем, ждем пока нам скажут что больше ситуацию улучшить не получится
            }

             debug.accept("OrderStartegy: " + os);
             debug.accept("Finish");

            //Как только вышли, значит все, лучше быть не может
            //Печатаем идентификаторы заказов, os.strategy это TreeSet, Taxi это Comparable - все корректно
            os.strategy.forEach((taxi, order) -> {
                System.out.println(order.id);
            });

            //Этот код падает на 7 тесте(wrong answer)  -- где-то ошибка
        }

        long takes = System.currentTimeMillis() - before;
        debug.accept("Takes: " + takes + " ms");
    }

    private static Taxi parseTaxiPositionString(int id, String s) {
        String[] carPositions = SPACE_PATTERN.split(s);
        return new Taxi(id, new Point(Integer.parseInt(carPositions[0]), Integer.parseInt(carPositions[1])));
    }

    private static Order parseOrderString(int id, String s) {
        String[] orderStrs = SPACE_PATTERN.split(s);
        Point begin = new Point(Integer.parseInt(orderStrs[0]), Integer.parseInt(orderStrs[1]));
        Point end = new Point(Integer.parseInt(orderStrs[2]), Integer.parseInt(orderStrs[3]));
        return new Order(id, begin, end);
    }


}
