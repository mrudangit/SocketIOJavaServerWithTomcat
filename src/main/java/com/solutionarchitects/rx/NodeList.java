package com.solutionarchitects.rx;

/**
 * Created by e211303 on 3/24/2016.
 */
/**
 * A singly-linked list with volatile next node pointer.
 * @param <T> the value type
 */
final class NodeList<T> {
    /**
     * The node containing the value and references to neighbours.
     * @param <T> the value type
     */
    static final class Node<T> {
        /** The managed value. */
        final T value;
        /** The hard reference to the next node. */
        volatile Node<T> next;
        Node(T value) {
            this.value = value;
        }
    }
    /** The head of the list. */
    final Node<T> head = new Node<T>(null);
    /** The tail of the list. */
    Node<T> tail = head;
    /** The number of elements in the list. */
    int size;

    public void addLast(T value) {
        Node<T> t = tail;
        Node<T> t2 = new Node<T>(value);
        t.next = t2;
        tail = t2;
        size++;
    }
    public T removeFirst() {
        if (head.next == null) {
            throw new IllegalStateException("Empty!");
        }
        Node<T> t = head.next;
        head.next = t.next;
        if (head.next == null) {
            tail = head;
        }
        size--;
        return t.value;
    }
    public boolean isEmpty() {
        return size == 0;
    }
    public int size() {
        return size;
    }
    public void clear() {
        tail = head;
        size = 0;
    }
}