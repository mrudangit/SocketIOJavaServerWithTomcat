package com.solutionarchitects.common.rx.v2;

import rx.Subscriber;
import rx.internal.operators.BackpressureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by montu on 8/6/16.
 */


final class ReplaySizeBoundBuffer<T> implements ReplayBuffer<T> {
    final int limit;

    volatile Node<T> head;

    Node<T> tail;

    int size;

    volatile boolean done;
    Throwable error;

    public ReplaySizeBoundBuffer(int limit) {
        this.limit = limit;
        Node<T> n = new Node<T>(null);
        this.tail = n;
        this.head = n;
    }

    @Override
    public void next(T value) {
        Node<T> n = new Node<T>(value);
        tail.set(n);
        tail = n;
        int s = size;
        if (s == limit) {
            head = head.get();
        } else {
            size = s + 1;
        }
    }

    @Override
    public void error(Throwable ex) {
        error = ex;
        done = true;
    }

    @Override
    public void complete() {
        done = true;
    }

    @Override
    public void drain(ReplayProducer<T> replayProducer) {
        if (replayProducer.getAndIncrement() != 0) {
            return;
        }

        final Subscriber<? super T> a = replayProducer.actual;

        int missed = 1;

        for (; ; ) {

            long r = replayProducer.requested.get();
            long e = 0L;

            @SuppressWarnings("unchecked")
            Node<T> node = (Node<T>) replayProducer.node;
            if (node == null) {
                node = head;
            }

            while (e != r) {
                if (a.isUnsubscribed()) {
                    replayProducer.node = null;
                    return;
                }

                boolean d = done;
                Node<T> next = node.get();
                boolean empty = next == null;

                if (d && empty) {
                    replayProducer.node = null;
                    Throwable ex = error;
                    if (ex != null) {
                        a.onError(ex);
                    } else {
                        a.onCompleted();
                    }
                    return;
                }

                if (empty) {
                    break;
                }

                a.onNext(next.value);

                e++;
                node = next;
            }

            if (e == r) {
                if (a.isUnsubscribed()) {
                    replayProducer.node = null;
                    return;
                }

                boolean d = done;
                boolean empty = node.get() == null;

                if (d && empty) {
                    replayProducer.node = null;
                    Throwable ex = error;
                    if (ex != null) {
                        a.onError(ex);
                    } else {
                        a.onCompleted();
                    }
                    return;
                }
            }

            if (e != 0L) {
                if (r != Long.MAX_VALUE) {
                    BackpressureUtils.produced(replayProducer.requested, e);
                }
            }

            replayProducer.node = node;

            missed = replayProducer.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    static final class Node<T> extends AtomicReference<Node<T>> {
        /** */
        private static final long serialVersionUID = 3713592843205853725L;

        final T value;

        public Node(T value) {
            this.value = value;
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public Throwable error() {
        return error;
    }

    @Override
    public T last() {
        Node<T> h = head;
        Node<T> n;
        while ((n = h.get()) != null) {
            h = n;
        }
        return h.value;
    }

    @Override
    public int size() {
        int s = 0;
        Node<T> n = head.get();
        while (n != null && s != Integer.MAX_VALUE) {
            n = n.get();
            s++;
        }
        return s;
    }

    @Override
    public boolean isEmpty() {
        return head.get() == null;
    }

    @Override
    public T[] toArray(T[] a) {
        List<T> list = new ArrayList<T>();

        Node<T> n = head.get();
        while (n != null) {
            list.add(n.value);
            n = n.get();
        }
        return list.toArray(a);
    }

}
