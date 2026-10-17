package com.nexusai.core;

public class LRUCacheTest {

    private static void check(
            boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        testBasicOperations();
        testEviction();
        testUpdatingExistingKey();
        testCapacityOne();
        testInvalidCapacity();

        System.out.println("All LRU cache tests passed!");
    }

    private static void testBasicOperations() {
        LRUCache<String, Integer> cache =
                new LRUCache<>(3);

        cache.put("A", 10);
        cache.put("B", 20);

        check(cache.get("A") == 10,
                "A should return 10");

        check(cache.get("Missing") == null,
                "Missing key should return null");

        check(cache.size() == 2,
                "Cache size should be 2");
    }

    private static void testEviction() {
        LRUCache<String, Integer> cache =
                new LRUCache<>(3);

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);

        cache.get("A"); // A is now most recently used.

        cache.put("D", 4); // B should be evicted.

        check(cache.containsKey("A"),
                "A should remain in the cache");

        check(!cache.containsKey("B"),
                "B should have been evicted");

        check(cache.get("C") == 3,
                "C should remain available");

        check(cache.get("D") == 4,
                "D should be available");

        check(cache.size() == 3,
                "Cache must not exceed capacity");
    }

    private static void testUpdatingExistingKey() {
        LRUCache<String, Integer> cache =
                new LRUCache<>(2);

        cache.put("A", 10);
        cache.put("B", 20);
        cache.put("A", 99);

        check(cache.get("A") == 99,
                "Updated value should be returned");

        cache.put("C", 30);

        check(cache.containsKey("A"),
                "Updated A should remain recently used");

        check(!cache.containsKey("B"),
                "B should be evicted");
    }

    private static void testCapacityOne() {
        LRUCache<String, Integer> cache =
                new LRUCache<>(1);

        cache.put("A", 1);
        cache.put("B", 2);

        check(!cache.containsKey("A"),
                "A should be evicted");

        check(cache.get("B") == 2,
                "B should remain");
    }

    private static void testInvalidCapacity() {
        boolean rejected = false;

        try {
            new LRUCache<String, Integer>(0);
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }

        check(rejected,
                "Zero capacity should be rejected");
    }
}