import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public class PointGroup {
    private static final boolean DEBUG_PRINT = false;

    //Реализовал два алгоритма, выбор - с помощью переменной
    private static final boolean PRECALC_MODE = false;
    //Из какого файла читать
    private static final String FILENAME = "input.txt";

    private static final Pattern SPACE_PATTERN = Pattern.compile(" ");

    private static void answer(int count) {
        System.out.println(count);
        System.exit(0);
    }

    private static TreeSet<Integer> parseIntSpaceDelimString(String s) {
        //Можно было бы написать в функциональном стиле, но "прогрев" Stream классов занимает много времени
        //Ибо нужно кучу классов загрузить - запускается у них там наверное 1 раз, так что попытаемся на этом съэкономить время
        if (s.isEmpty())
            return new TreeSet<>();
        String[] split = SPACE_PATTERN.split(s);
        TreeSet<Integer> aset = new TreeSet<>();
        for (String n : split) {
            //Ничего не обрабатываем, вход должен быть корректным
            aset.add(Integer.parseInt(n));
        }
        return aset;
    }

    public static void main(String[] args) throws IOException {
        long before = System.currentTimeMillis();
        File f = new File(FILENAME);
        if (f.exists()) {
            List<String> allLines = Files.readAllLines(f.toPath());
            //Считываем файл, извлекаем строки
            if (allLines.size() == 2) {
                //Разбираем заголовок
                String header = allLines.get(0);
                String[] headerVars = SPACE_PATTERN.split(header);
                //Общее кол-во, особо не нужно ибо парсим всю строку, считаем что вход корректный
                int totalCount = Integer.parseInt(headerVars[0]);
                int r = Integer.parseInt(headerVars[1]);

                //Извлекли точки причем оставляем только уникальные
                TreeSet<Integer> points = parseIntSpaceDelimString(allLines.get(1));
                //Выполняем алгоритм в зависимости от настроек
                int result = PRECALC_MODE ? precalcMode(points, r) : straightMode(points, r);
                answer(result);
            } else if (allLines.size() == 1) {
                //Нет точек
                answer(0);
            } else {
                //Не известный формат файла
                answer(0);
            }
        } else {
            //Файл не найден
            answer(0);
        }

        if (DEBUG_PRINT) {
            long takes = System.currentTimeMillis() - before;
            System.out.println("Takes " + takes + " ms");
        }

    }

    private static boolean reachable(Integer o1, Integer o2, int r) {
        return Math.abs(o1 - o2) <= r;
    }

    //Правильный, быстрый алгоритм, за 1 проход
    private static int straightMode(TreeSet<Integer> points, int r) {

        //Вырожденные случаи, не понятно что там на вход подается, наверника и такое есть
        if (points.isEmpty())
            return 0;

        if (points.size() == 1)
            return 1;

        Integer prev = null; //Предыдущая точка в процессе итерации
        Integer leftBound = null; //Левая граница - это самая "левая" точка, которая еще "не покрыта" ключевой точкой
        //Это те ключевые точки которые мы выбрали в процессе работы алгоритма
        TreeSet<Integer> keyPoints = new TreeSet<>();

        //Точки отсортированны, проходимся по ним
        for (Integer i : points) {

            Boolean canBeReached = null;
            //Если есть не покрытая точка
            if (leftBound != null) {
                //Проверяем, "дотягиваемся" ли мы из текущей точки до этой левой границы
                canBeReached = reachable(leftBound, i, r);

                //Если не дотягиваемся, то предыдущая точка - ключевая
                if (!canBeReached) {

                    keyPoints.add(prev);
                    //Появилась новая ключевая точка, значит нужно переназначить левую границу:
                    //вопрос в чем - можем ли мы дотянутся из последней ключевой точки(это prev мы ее только что добавили) до ТЕКУЩЕЙ?
                    //Если можем - значит левую границу следует устанавливать на следующей итерации, а если не можем - то левая граница это текущая точка
                    leftBound = reachable(prev, i, r) ? null : i;
                }
            } else {
                //Такое может быть если это первая итерация, или если мы не стали переназначать левую границу в if-е выше

                //Левую границу переназначем только в случае, если эта точка недостижима из последней ключевой
                if (keyPoints.isEmpty() || !reachable(keyPoints.last(), i, r))
                    leftBound = i;
            }

            if (DEBUG_PRINT)
                System.out.printf("i: %d , prev: %d , leftBound: %d(reached: %s), kp: %s \n", i, prev, leftBound, canBeReached, keyPoints);

            prev = i;
        }

        //Для последней итерации особые условия - проверяем, дотягиваемся ли мы из своей последней ключевой точки, до последней точки в наборе
        if (keyPoints.isEmpty() || !reachable(keyPoints.last(), prev, r)) {
            //Первый вариант может быть тогда, когда мы дотягивались до всех точек в процессе итерации
            keyPoints.add(prev);
        }

        return keyPoints.size();
    }

    //Альтернативный вариант расчета, оч долгий, не пройдет за требуемое время
    private static int precalcMode(TreeSet<Integer> points, int r) {
        //Массив содержащий все точки, и какие точки из них могут быть достигнуты с заданным R
        ArrayList<CorePointDTO> calculatedPoints = new ArrayList<>(points.size());

        //Считаем расстояние от всех точек до всех точек и сохраняем
        for (Integer i : points) {


            //Это список точек, которые достижимы из i-ой точки
            TreeSet<Integer> reachedPoints = new TreeSet<>();

            //Поскольку они отсортированы, можем оптимизировать расчет
            Boolean prevState = null;
            for (Integer j : points) {
                //Суть в чем - как только у нас произошло измение с ДОСТИЖИМО на НЕ достижимо - можно останавливатся
                //дальше уже лучше не будет, ибо идем от меньшего к большему
                boolean canBeReached = Math.abs(j - i) <= r;
                if (canBeReached) reachedPoints.add(j);
                if (prevState != null && prevState && !canBeReached)
                    break;
                prevState = canBeReached;
            }
            //Сохраняем DTO
            CorePointDTO dto = new CorePointDTO(i, reachedPoints);
            calculatedPoints.add(dto);
        }

        if (DEBUG_PRINT)
            System.out.println("Queue:" + calculatedPoints);

        //Ответ наш
        int ctx = 0;

        //Продолжаем пока не покроем все точки, исходим из того, что ответ есть полюбому

        while (!points.isEmpty()) {
            //Ищем лучшего кандидата
            CorePointDTO bestCandidate = null;
            int bestCandidateMatch = 0;

            //Проходимся по всем точкам и ищем какая точка лучше всего нам подойдет

            //По сути заново изобрел этот жадный алгоритм
            //https://ru.m.wikipedia.org/wiki/Задача_о_покрытии_множества
            for (CorePointDTO dto : calculatedPoints) {
                //Расчитываем "вес" - сколько новых точек в общий сет привнесет эта точка при ее добавлении в ответ
                int thisCandidateMatch = 0;

                //Если ранее такой точки у нас не было - то вес увеличивается
                Set<Integer> hasGoodDistance = dto.hasGoodDistance;
                for (Integer c : hasGoodDistance) {
                    if (points.contains(c))
                        thisCandidateMatch++;
                }

                //1 итерация, или же мы нашли лучший вариант чем раньше
                if (bestCandidateMatch < thisCandidateMatch) {
                    bestCandidateMatch = thisCandidateMatch;
                    bestCandidate = dto;
                }
            }

            //Добавляем в результат
            if (DEBUG_PRINT)
                System.out.println("Best candidate: " + bestCandidate + " \t Match: " + bestCandidateMatch);

            //Т.к. мы покрыли эти точки - мы их удаляем из нашего списка
            points.removeAll(bestCandidate.hasGoodDistance);
            ctx++;
        }
        return ctx;
    }

    //DTO для алгоритма с предрасчетом, точка и список достижимых точек для нее
    static class CorePointDTO {
        int value; //Значение точки
        Set<Integer> hasGoodDistance; //Какие точки достижимы с заданным R из этой точки

        public CorePointDTO(int value, Set<Integer> hasGoodDistance) {
            this.value = value;
            this.hasGoodDistance = hasGoodDistance;
        }

        @Override
        public String toString() {
            return "CorePointDTO{" +
                    "value=" + value +
                    ", hasGoodDistance=" + hasGoodDistance +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CorePointDTO that = (CorePointDTO) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {

            return Objects.hash(value);
        }
    }
}
