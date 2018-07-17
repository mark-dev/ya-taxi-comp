import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public class First {
    private static final boolean DEBUG = false;
    private static final String FILENAME = "1/input_big.txt";

    private static final Pattern SPACE_PATTERN = Pattern.compile(" ");

    private static void answer(int count) {
        System.out.println(count);
    }

    private static TreeSet<Integer> parseSpaceDelimString(String s) {
        //Можно было бы написать в функциональном стиле, но "прогрев" Stream классов занимает много времени
        //Ибо нужно кучу классов загрузить
        String[] split = SPACE_PATTERN.split(s);
        TreeSet<Integer> aset = new TreeSet<>();
        for (String n : split) {
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
                String header = allLines.get(0);
                String[] headerVars = SPACE_PATTERN.split(header);
                //Общее кол-во, особо не нужно ибо парсим всю строку
                int totalCount = Integer.parseInt(headerVars[0]);
                int r = Integer.parseInt(headerVars[1]);

                //Извлекли точки причем оставляем только уникальные
                TreeSet<Integer> points = parseSpaceDelimString(allLines.get(1));
//                int result = precalcMode(points,r);
                int result = straightMode(points, r);
                answer(result);
            } else if (allLines.size() == 1) {
                answer(0);
            } else {
                System.out.println("Unknown file format");
            }
        } else {
            System.out.println("File not found");
        }
        if (DEBUG) {
            long takes = System.currentTimeMillis() - before;
            System.out.println("Takes " + takes + " ms");
        }
    }

    private static boolean reachable(Integer o1, Integer o2, int r) {
        return Math.abs(o1 - o2) <= r;
    }

    private static int straightMode(TreeSet<Integer> points, int r) {
        //Предыдущая
        Integer prev = null; //Предыдущая точка в процессе итерации
        Integer leftBound = null; //Левая граница - это самая "левая" точка, которая еще "не покрыта" ключевой точкой
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
                    //Нужно переназначить левую границу:
                    //вопрос в чем - можем ли мы дотянутся из последней ключевой точки(это prev мы ее только что добавили) до ТЕКУЩЕЙ?
                    //Если можем - значит левую границу следует устанавливать на следующей итерации, а если не можем - то левая граница это текущая точка
                    //
                    leftBound = reachable(prev, i, r) ? null : i;
                }
            } else {
                leftBound = i;
            }

            if (DEBUG)
                System.out.printf("i: %d , prev: %d , leftBound: %d(reached: %s), kp: %s \n", i, prev, leftBound, canBeReached, keyPoints);

            prev = i;
        }
        //Для последней итерации особые условия - проверяем, дотягиваемся ли мы из своей последней ключевой точки, до последней точки в наборе
        if (!reachable(keyPoints.last(), prev, r)) {
            keyPoints.add(prev);
        }
        return keyPoints.size();
    }


    private static int precalcMode(TreeSet<Integer> points, int r) {
        //Массив содержащий все точки, и какие точки из них могут быть достигнуты с заданным R
        ArrayList<CorePointDTO> calculatedPoints = new ArrayList<>(points.size());

        //Считаем расстояние от всех точек до всех точек и сохраняем
        for (Integer i : points) {

            //К слову, они же отсортированны будут в Set.. мб можно оптимизировать как-то?
            //Грубо говоря как-только первый раз не удалось посчитать расстояние - то все, дальше можно не считать, ибо расстояние только расти будет

            TreeSet<Integer> reachedPoints = new TreeSet<>();
            Boolean prevState = null;
            for (Integer j : points) {
                boolean canBeReached = Math.abs(j - i) <= r;
                if (canBeReached) reachedPoints.add(j);
                if (prevState != null && prevState && !canBeReached)
                    break;
                prevState = canBeReached;
            }
            CorePointDTO dto = new CorePointDTO(i, reachedPoints);
            calculatedPoints.add(dto);
        }
        //  System.out.println("Queue:" + calculatedPoints);

        //Ответ наш
        int ctx = 0;

        //Пока не найдем достаточное кол-во точек - итерируем, исходим из того, что ответ есть полюбому

        while (!points.isEmpty()) {
            //Ищем лучшего кандидата
            CorePointDTO bestCandidate = null;
            int bestCandidateMatch = 0;

            //Проходимся по всем точкам
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
            System.out.println("Best candidate: " + bestCandidate + " \t Match: " + bestCandidateMatch);
            points.removeAll(bestCandidate.hasGoodDistance);
            ctx++;
        }
        return ctx;
    }

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
