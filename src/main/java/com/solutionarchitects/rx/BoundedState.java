package com.solutionarchitects.rx;

/**
 * Created by e211303 on 3/24/2016.
 */

import rx.Observer;
import rx.functions.Func1;
import rx.internal.operators.NotificationLite;

import java.util.ArrayList;
import java.util.List;

/**
 * The bounded replay state.
 * @param <T> the input and output type
 */
 final class BoundedState<T> implements ReplayState<T, NodeList.Node<Object>> {
    final NodeList<Object> list;
    final EvictionPolicy evictionPolicy;
    final Func1<Object, Object> enterTransform;
    final Func1<Object, Object> leaveTransform;
    final NotificationLite<T> nl = NotificationLite.instance();
    volatile boolean terminated;
    volatile NodeList.Node<Object> tail;

    public BoundedState(EvictionPolicy evictionPolicy, Func1<Object, Object> enterTransform,
                        Func1<Object, Object> leaveTransform) {
        this.list = new NodeList<Object>();
        this.tail = list.tail;
        this.evictionPolicy = evictionPolicy;
        this.enterTransform = enterTransform;
        this.leaveTransform = leaveTransform;
    }
    @Override
    public void next(T value) {
        if (!terminated) {
            list.addLast(enterTransform.call(nl.next(value)));
            evictionPolicy.evict(list);
            tail = list.tail;
        }
    }
    @Override
    public void complete() {
        if (!terminated) {
            terminated = true;
            list.addLast(enterTransform.call(nl.completed()));
            evictionPolicy.evictFinal(list);
            tail = list.tail;
        }

    }
    @Override
    public void error(Throwable e) {
        if (!terminated) {
            terminated = true;
            list.addLast(enterTransform.call(nl.error(e)));
            // don't evict the terminal value
            evictionPolicy.evictFinal(list);
            tail = list.tail;
        }
    }
    public void accept(Observer<? super T> o, NodeList.Node<Object> node) {
        nl.accept(o, leaveTransform.call(node.value));
    }
    /**
     * Accept only non-stale nodes.
     * @param o the target observer
     * @param node the node to accept or reject
     * @param now the current time
     */
    public void acceptTest(Observer<? super T> o, NodeList.Node<Object> node, long now) {
        Object v = node.value;
        if (!evictionPolicy.test(v, now)) {
            nl.accept(o, leaveTransform.call(v));
        }
    }
    public NodeList.Node<Object> head() {
        return list.head;
    }
    public NodeList.Node<Object> tail() {
        return tail;
    }
    @Override
    public boolean replayObserver(SubjectSubscriptionManager.SubjectObserver<? super T> observer) {
        synchronized (observer) {
            observer.first = false;
            if (observer.emitting) {
                return false;
            }
        }

        NodeList.Node<Object> lastEmittedLink = observer.index();
        NodeList.Node<Object> l = replayObserverFromIndex(lastEmittedLink, observer);
        observer.index(l);
        return true;
    }

    @Override
    public NodeList.Node<Object> replayObserverFromIndex(
            NodeList.Node<Object> l, SubjectSubscriptionManager.SubjectObserver<? super T> observer) {
        while (l != tail()) {
            accept(observer, l.next);
            l = l.next;
        }
        return l;
    }
    @Override
    public NodeList.Node<Object> replayObserverFromIndexTest(
            NodeList.Node<Object> l, SubjectSubscriptionManager.SubjectObserver<? super T> observer, long now) {
        while (l != tail()) {
            acceptTest(observer, l.next, now);
            l = l.next;
        }
        return l;
    }

    @Override
    public boolean terminated() {
        return terminated;
    }

    @Override
    public int size() {
        int size = 0;
        NodeList.Node<Object> l = head();
        NodeList.Node<Object> next = l.next;
        while (next != null) {
            size++;
            l = next;
            next = next.next;
        }
        if (l.value != null) {
            Object value = leaveTransform.call(l.value);
            if (value != null && (nl.isError(value) || nl.isCompleted(value))) {
                return size - 1;
            }
        }
        return size;
    }
    @Override
    public boolean isEmpty() {
        NodeList.Node<Object> l = head();
        NodeList.Node<Object> next = l.next;
        if (next == null) {
            return true;
        }
        Object value = leaveTransform.call(next.value);
        return nl.isError(value) || nl.isCompleted(value);
    }
    @Override
    @SuppressWarnings("unchecked")
    public T[] toArray(T[] a) {
        List<T> list = new ArrayList<T>();
        NodeList.Node<Object> l = head();
        NodeList.Node<Object> next = l.next;
        while (next != null) {
            Object o = leaveTransform.call(next.value);

            if (next.next == null && (nl.isError(o) || nl.isCompleted(o))) {
                break;
            } else {
                list.add((T)o);
            }
            l = next;
            next = next.next;
        }
        return list.toArray(a);
    }
    @Override
    public T latest() {
        NodeList.Node<Object> h = head().next;
        if (h == null) {
            return null;
        }
        NodeList.Node<Object> p = null;
        while (h != tail()) {
            p = h;
            h = h.next;
        }
        Object value = leaveTransform.call(h.value);
        if (nl.isError(value) || nl.isCompleted(value)) {
            if (p != null) {
                value = leaveTransform.call(p.value);
                return nl.getValue(value);
            }
            return null;
        }
        return nl.getValue(value);
    }
}

