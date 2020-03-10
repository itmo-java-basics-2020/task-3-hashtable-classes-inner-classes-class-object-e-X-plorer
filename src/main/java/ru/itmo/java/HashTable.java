package ru.itmo.java;

import java.util.Map;

public class HashTable {

    private final LoopTerminatorPredicate additionPredicate = (currentIndex, table, keyToAdd) ->
            table[currentIndex % table.length] != null && !table[currentIndex % table.length].key.equals(keyToAdd);
    private final LoopTerminatorPredicate getPredicate = (currentIndex, table, keyToFind) ->
            table[currentIndex % table.length] != null && (!table[currentIndex % table.length].key.equals(keyToFind) || table[currentIndex % table.length].deleted);
    private final LoopTerminatorPredicate removePredicate = (currentIndex, table, keyToFind) ->
            table[currentIndex % table.length] != null && !table[currentIndex % table.length].key.equals(keyToFind);

    private Entry[] table;
    private int count = 0;
    private int dirty = 0;
    private float loadFactor = 0.5f;

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
        int hc = key.hashCode() * 0x7ffffff, step = 0;
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
        if (count + dirty < threshold()) {
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
        return true;
    }

    Object put(Object key, Object value) {
        checkCapacityAndExpand();
        int index = find(key, Operation.PUT);
        Object prev = null;
        if (table[index] == null) {
            count++;
        } else if (table[index].deleted) {
            dirty--;
            count++;
        } else {
            prev = table[index].value;
        }
        table[index] = new Entry(key, value);
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
        return (int) Math.min(table.length * loadFactor, table.length * 0.8 + 0.2);
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

    private enum Operation {PUT, GET, REMOVE}
}
