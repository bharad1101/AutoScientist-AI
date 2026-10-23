package com.autoscientist.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Small thread-safe LRU cache for expensive read-only analysis results.
 * Keys embed the dataset version, so re-profiling or changing column
 * exclusions invalidates entries automatically.
 */
public final class BoundedCache<K, V> {

    private final Map<K, V> map;

    public BoundedCache(int capacity) {
        final int max = Math.max(1, capacity);
        this.map = new LinkedHashMap<>(max * 2, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        };
    }

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized void put(K key, V value) {
        map.put(key, value);
    }

    public synchronized V getOrCompute(K key, Supplier<V> supplier) {
        V existing = map.get(key);
        if (existing != null) return existing;
        V computed = supplier.get();
        if (computed != null) map.put(key, computed);
        return computed;
    }

    public synchronized void invalidate(K key) {
        map.remove(key);
    }

    public synchronized void invalidatePrefix(String prefix) {
        if (prefix == null) return;
        map.keySet().removeIf(k -> k instanceof String && ((String) k).startsWith(prefix));
    }

    public synchronized void clear() {
        map.clear();
    }

    public synchronized int size() {
        return map.size();
    }
}
