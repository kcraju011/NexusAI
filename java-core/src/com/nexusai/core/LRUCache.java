package com.nexusai.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LRUCache<K, V> {

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> cache;

    // Sentinel nodes simplify insertion and removal.
    private final Node<K, V> head = new Node<>(null, null);
    private final Node<K, V> tail = new Node<>(null, null);

    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Capacity must be positive"
            );
        }

        this.capacity = capacity;
        this.cache = new HashMap<>();

        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        Objects.requireNonNull(key, "Key cannot be null");

        Node<K, V> node = cache.get(key);

        if (node == null) {
            return null;
        }

        moveToFront(node);
        return node.value;
    }

    public void put(K key, V value) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");

        Node<K, V> existing = cache.get(key);

        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }

        Node<K, V> node = new Node<>(key, value);

        cache.put(key, node);
        addToFront(node);

        if (cache.size() > capacity) {
            Node<K, V> leastRecentlyUsed = removeLast();
            cache.remove(leastRecentlyUsed.key);
        }
    }

    private void moveToFront(Node<K, V> node) {
        removeNode(node);
        addToFront(node);
    }

    private void addToFront(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;

        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private Node<K, V> removeLast() {
        Node<K, V> node = tail.prev;
        removeNode(node);
        return node;
    }

    public int size() {
        return cache.size();
    }

    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }
}