package ru.itmo.java;

import java.util.Map;

public class HashTable {

    private static final float LOAD_FACTOR_DEVIATION = 0.1f;
    private static final float LOAD_FACTOR_MAX_VALUE = 0.95f;
    private static final float DEFAULT_LOAD_FACTOR = 0.5f;

    private enum Operation {PUT, GET, REMOVE}

    private static final LoopTerminatorPredicate additionPredicate = (currentIndex, table, keyToAdd) ->
            table[currentIndex % table.length] != null && !table[currentIndex % table.length].deleted;
    private static final LoopTerminatorPredicate getPredicate = (currentIndex, table, keyToFind) ->
            table[currentIndex % table.length] != null && (!table[currentIndex % table.length].key.equals(keyToFind) || table[currentIndex % table.length].deleted);
    private static final LoopTerminatorPredicate removePredicate = (currentIndex, table, keyToFind) ->
            table[currentIndex % table.length] != null && !table[currentIndex % table.length].key.equals(keyToFind);

    private Entry[] table;
    private int count = 0;
    private int dirty = 0;
    private float loadFactor = DEFAULT_LOAD_FACTOR;

    public HashTable(int initialCapacity) {
        table = new Entry[initialCapacity];
    }

    public HashTable(int initialCapacity, float loadFactor) {
        this(initialCapacity);
        this.loadFactor = loadFactor;
    }

    private int find(Object key, Operation operation) {
        switch (operation) {
            case PUT:
                return find(key, table, additionPredicate);
            case GET:
                return find(key, table, getPredicate);
            case REMOVE:
                return find(key, table, removePredicate);
            default:
                return -1;
        }
    }

    private int find(Object key, Entry[] table, LoopTerminatorPredicate predicate) {
        int hc = key.hashCode() * Integer.MAX_VALUE, step = 0;
        do {
            hc += step * step;
            if (hc < 0) {
                hc -= Integer.MIN_VALUE;
            }
            step++;
        } while (predicate.operation(hc, table, key));

        return hc % table.length;
    }

    private boolean checkCapacityAndExpand() {
        if (count < threshold() && count + dirty < table.length) {
            return false;
        }
        Entry[] newTable = new Entry[table.length * 2];
        for (Entry entry : table) {
            if (entry == null || entry.deleted) {
                continue;
            }
            newTable[find(entry.key, newTable, additionPredicate)] = entry;
        }
        table = newTable;
        dirty = 0;
        return true;
    }

    Object put(Object key, Object value) {
        int index = find(key, Operation.GET);
        Object prev = null;
        if (table[index] != null) {
            prev = table[index].value;
        } else {
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

    Object get(Object key) {
        int index = find(key, Operation.GET);
        if (table[index] == null) {
            return null;
        }
        return table[index].value;
    }

    Object remove(Object key) {
        int index = find(key, Operation.REMOVE);
        if (table[index] == null || table[index].deleted) {
            return null;
        }
        table[index].deleted = true;
        count--;
        dirty++;
        return table[index].value;
    }

    int size() {
        return count;
    }

    int threshold() {
        return (int) (table.length * Math.min(loadFactor + LOAD_FACTOR_DEVIATION, LOAD_FACTOR_MAX_VALUE));
    }

    private static class Entry {
        private final Object key;
        private final Object value;
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
