package ru.itmo.java;

import java.util.Map;

public class HashTable {

    private static final float LOAD_FACTOR_DEVIATION = 0.1f;
    private static final float LOAD_FACTOR_MAX_VALUE = 0.95f;
    private static final float DEFAULT_LOAD_FACTOR = 0.5f;

    private static final LoopTerminatorPredicate ADDITION_PREDICATE = (currentIndex, table, keyToAdd) ->
            table[currentIndex % table.length] != null && !table[currentIndex % table.length].deleted;
    private static final LoopTerminatorPredicate GET_PREDICATE = (currentIndex, table, keyToFind) ->
            table[currentIndex % table.length] != null && (table[currentIndex % table.length].deleted || !table[currentIndex % table.length].key.equals(keyToFind));
    private static final LoopTerminatorPredicate REMOVE_PREDICATE = (currentIndex, table, keyToFind) ->
            table[currentIndex % table.length] != null && !table[currentIndex % table.length].key.equals(keyToFind);

    private Entry[] table;
    private int count = 0;  /* Показывает, сколько ячеек таблицы заполнено реальными значениями */
    private int dirty = 0;  /* Показывает, сколько ячеек таблицы заполнено удаленными значениями */
    private float loadFactor = DEFAULT_LOAD_FACTOR;

    public HashTable(int initialCapacity) {
        if (initialCapacity < 1) {
            System.out.println("Invalid initial capacity. Capacity of 1 will be used.");
            initialCapacity = 1;
        }
        table = new Entry[initialCapacity];
    }

    public HashTable(int initialCapacity, float loadFactor) {
        this(initialCapacity);
        if (loadFactor < 0 || loadFactor > 1) {
            System.out.println(String.format("Invalid loadFactor. Fallback value of %.2f will be used.", DEFAULT_LOAD_FACTOR));
            loadFactor = DEFAULT_LOAD_FACTOR;
        }
        this.loadFactor = loadFactor;
    }

    public Object put(Object key, Object value) {

        /* При первом поиске проверяем, находится ли в таблице ячейка с таким же ключом, то есть такая,
         * value которой нужно изменить. Необходимость отдельной проверки обуславливается тем, что при
         * удалении можно положить DELETED в ячейку с тем же начальным хеш-кодом, что и у той, которую
         * в будущем нам нужно будет изменить через put(), но расположенную в таблице раньше. Затем при
         * поиске ячейки для put() алгоритм найдет эту DELETED ячейку и положит новый элемент туда, не
         * дойдя до уже сушествующего элемента с нужным нам ключом.
         *
         * Изначально я пытался исользовать ту же логику, что и в моем коде хеш-таблицы для "Алгоритмов
         * и структур данных", но, как оказались, те тесты такую ситуацию не покрывали. Похожая логика,
         * описанная в интернете для операции добавления, также не работала.
         */
        int index = find(key, Operation.GET);
        Object prev = null;
        if (table[index] != null) {
            prev = table[index].value;
        } else {

            /* Второй поиск с другой логикой (он уже ищет первую же попавшуюся не заполненную реальным
             * значением ячейку) запускается только в том случае, если первый поиск не нашел элемента
             * с переданным в метод ключом. Поэтому во втором случае всегда возвращается null, то есть
             * неправильный prev мы не вернем.
             */
            index = find(key, Operation.PUT);
            count++;
            if (table[index] != null) {
                dirty--;
            }
        }
        table[index] = new Entry(key, value);
        checkCapacityAndExpand();
        return prev;
    }

    public Object get(Object key) {

        /* В случае, если нужно лишь получить значение, хранящееся по данному ключу, можно создать
         * метод поиска, который будет сразу возвращать это значение или пару ключ-значение.
         * Однако в таком случае придется создавать дополнительный метод, большая часть логики
         * которого будет совпадать с существующим find. При этом нельзя сделать так, чтобы find
         * для put() и remove() возвращали сразу пару, так как они требуют изменения хранящихся
         * в таблице значений. Если сделать key и value изменяетмыми значениями в Entry, то это
         * будет возможно. Если так будет лучше, то я могу так сделать.
         */
        int index = find(key, Operation.GET);
        if (table[index] == null) {
            return null;
        }
        return table[index].value;
    }

    public Object remove(Object key) {
        int index = find(key, Operation.REMOVE);
        if (table[index] == null || table[index].deleted) {
            return null;
        }
        table[index].deleted = true;
        count--;
        dirty++;
        return table[index].value;
    }

    public int size() {
        return count;
    }

    private int threshold() {
        return (int) (table.length * Math.min(loadFactor + LOAD_FACTOR_DEVIATION, LOAD_FACTOR_MAX_VALUE));
    }

    private int find(Object key, Operation operation) {
        switch (operation) {
            case PUT:
                return find(key, table, ADDITION_PREDICATE);
            case GET:
                return find(key, table, GET_PREDICATE);
            case REMOVE:
                return find(key, table, REMOVE_PREDICATE);
            default:
                return -1;
        }
    }

    private int find(Object key, Entry[] table, LoopTerminatorPredicate predicate) {
        int hc = key.hashCode() * Integer.MAX_VALUE;
        int step = 0;
        do {

            /* В случае совпадения хеша мы делаем квадратичный поиск, то есть рассматриваем ячейки
             * <actualHashCode> + 1, <actualHashCode> + 4, <actualHashCode> + 9 и т.д.
             * При большой числе шагов может произойти переполнение и переход к отрицательным числам.
             * Так как в предикате не используется взятие модуля или умножение на Integer.MAX_VALUE,
             * нужно не допустить передачи в предикат отрицательного числа. Вычитая из отрицательного
             * числа Integer.MIN_VALUE, мы получаем такое неотрицательное число, которые получили бы,
             * если бы в Java существовал беззнаковый Integer.
             */
            hc += step * step;
            if (hc < 0) {
                hc -= Integer.MIN_VALUE;
            }
            step++;
        } while (predicate.operation(hc, table, key));

        return hc % table.length;
    }

    private boolean checkCapacityAndExpand() {

        /* Первое условие выполняет проверку согласно тому, что threshold() возращает количество ячеек,
         * которое должно быть заполнено реальными значениями перед расширением таблицы. Однако у нас
         * есть и некоторое количество ячеек, заполненных удаленными значениями (dirty). Если получится
         * так, что после put() в таблице не останется ни одной абсолютно пустой ячейки, но при этом число
         * ячеек, заполненных реальными занениями, меньше threshold(), то после этого операция поиска для,
         * например, get() может уйти в бесконечный цикл, не находя ни требуемого значения, ни null (ведь
         * null'ов не осталось). Второе условие нужно для обработки этой редкой ситуации и сохранения по
         * крайней мере одной пустой ячейки в таблице (если ее нет, то расширяем таблицу). Нельзя просто
         * учитывать dirty в первом условии, так как в таком случае расширения будут происходить чаще, чем
         * необходимо, ведь методу put() не мешают заполненные удаленными значениями ячейки.
         */
        if (count < threshold() && count + dirty < table.length) {
            return false;
        }
        Entry[] newTable = new Entry[table.length * 2];
        for (Entry entry : table) {
            if (entry == null || entry.deleted) {
                continue;
            }
            newTable[find(entry.key, newTable, ADDITION_PREDICATE)] = entry;
        }
        table = newTable;
        dirty = 0;
        return true;
    }

    /* В Java code code conventions не указано правил по расположению перечислений в теле класса.
     * Учитывая, что перечисление является классом, его можно поместить там же, где объявляются
     * внутренние классы.
     */
    private enum Operation {
        PUT, GET, REMOVE
    }

    private static class Entry {
        private final Object key;
        private final Object value;

        /* Удалить это поле нельзя, так как в данной реализации необходимо иметь информацию
         * о ключах удаленных элементов.
         */
        private boolean deleted = false;

        public Entry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    private interface LoopTerminatorPredicate {
        boolean operation(int currentIndex, Entry[] table, Object key);
    }
}
