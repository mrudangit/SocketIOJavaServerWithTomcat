package com.solutionarchitects.common.rx.v2;

/**
 * Created by montu on 8/6/16.
 */

import rx.Subscriber;
import rx.internal.operators.BackpressureUtils;

import java.lang.reflect.Array;

/**
 * An unbounded ReplayBuffer implementation that uses linked-arrays
 * to avoid copy-on-grow situation with ArrayList.
 *
 * @param <T> the value type
 */
final class ReplayUnboundedBuffer<T> implements ReplayBuffer<T> {
    final int capacity;

    volatile int size;

    final Object[] head;

    Object[] tail;

    int tailIndex;

    volatile boolean done;
    Throwable error;

    public ReplayUnboundedBuffer(int capacity) {
        this.capacity = capacity;
        this.tail = this.head = new Object[capacity + 1];
    }

    @Override
    public void next(T t) {
        if (done) {
            return;
        }
        int i = tailIndex;
        Object[] a = tail;
        if (i == a.length - 1) {
            Object[] b = new Object[a.length];
            b[0] = t;
            tailIndex = 1;
            a[i] = b;
            tail = b;
        } else {
            a[i] = t;
            tailIndex = i + 1;
        }
        size++;

    }

    @Override
    public void error(Throwable e) {
        if (done) {
            RxJavaHooks.onError(e);
            return;
        }
        error = e;
        done = true;
    }

    @Override
    public void complete() {
        done = true;
    }

    @Override
    public void drain(ReplayProducer<T> rp) {
        if (rp.getAndIncrement() != 0) {
            return;
        }

        int missed = 1;

        final Subscriber<? super T> a = rp.actual;
        final int n = capacity;

        for (; ; ) {

            long r = rp.requested.get();
            long e = 0L;

            Object[] node = (Object[]) rp.node;
            if (node == null) {
                node = head;
            }
            int tailIndex = rp.tailIndex;
            int index = rp.index;

            while (e != r) {
                if (a.isUnsubscribed()) {
                    rp.node = null;
                    return;
                }

                boolean d = done;
                boolean empty = index == size;

                if (d && empty) {
                    rp.node = null;
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

                if (tailIndex == n) {
                    node = (Object[]) node[tailIndex];
                    tailIndex = 0;
                }

                @SuppressWarnings("unchecked")
                T v = (T) node[tailIndex];

                a.onNext(v);

                e++;
                tailIndex++;
                index++;
            }

            if (e == r) {
                if (a.isUnsubscribed()) {
                    rp.node = null;
                    return;
                }

                boolean d = done;
                boolean empty = index == size;

                if (d && empty) {
                    rp.node = null;
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
                    BackpressureUtils.produced(rp.requested, e);
                }
            }

            rp.index = index;
            rp.tailIndex = tailIndex;
            rp.node = node;

            missed = rp.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
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

    @SuppressWarnings("unchecked")
    @Override
    public T last() {
        // we don't have a volatile read on tail and tailIndex
        // so we have to traverse the linked structure up until
        // we read size / capacity nodes and index into the array
        // via size % capacity
        int s = size;
        if (s == 0) {
            return null;
        }
        Object[] h = head;
        int n = capacity;

        while (s >= n) {
            h = (Object[]) h[n];
            s -= n;
        }

        return (T) h[s - 1];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T[] toArray(T[] a) {
        int s = size;
        if (a.length < s) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), s);
        }

        Object[] h = head;
        int n = capacity;

        int j = 0;

        while (j + n < s) {
            System.arraycopy(h, 0, a, j, n);
            j += n;
            h = (Object[]) h[n];
        }

        System.arraycopy(h, 0, a, j, s - j);

        if (a.length > s) {
            a[s] = null;
        }

        return a;
    }
}
