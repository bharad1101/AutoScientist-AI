package com.autoscientist;

import com.autoscientist.util.BoundedCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BoundedCacheTest {

    @Test
    @DisplayName("LRU cache computes once, evicts oldest entries and honours capacity")
    void testLruBehaviour() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        AtomicInteger computes = new AtomicInteger();
        assertEquals("a", cache.getOrCompute("a", () -> { computes.incrementAndGet(); return "a"; }));
        assertEquals("a", cache.getOrCompute("a", () -> { computes.incrementAndGet(); return "a"; }));
        assertEquals(1, computes.get(), "second lookup must hit the cache");

        cache.put("b", "b");
        cache.get("a");
        cache.put("c", "c");
        assertEquals(2, cache.size());
        assertNull(cache.get("b"), "least-recently-used entry is evicted");
        assertEquals("a", cache.get("a"));
    }

    @Test
    @DisplayName("Invalidates keys matching a given prefix and clears entire cache safely")
    void testInvalidatePrefixAndClear() {
        BoundedCache<String, String> cache = new BoundedCache<>(10);
        cache.put("1@1:correlations", "val1");
        cache.put("1@1:boxplots", "val2");
        cache.put("2@1:correlations", "val3");

        assertEquals(3, cache.size());
        cache.invalidatePrefix("1@");

        assertEquals(1, cache.size());
        assertNull(cache.get("1@1:correlations"));
        assertNull(cache.get("1@1:boxplots"));
        assertEquals("val3", cache.get("2@1:correlations"));

        cache.clear();
        assertEquals(0, cache.size());
    }
}
