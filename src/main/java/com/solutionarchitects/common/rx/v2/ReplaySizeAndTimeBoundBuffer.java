package com.solutionarchitects.common.rx.v2;

import rx.Scheduler;
import rx.Subscriber;
import rx.internal.operators.BackpressureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by montu on 8/6/16.
 */
final class ReplaySizeAndTimeBoundBuffer<T> implements ReplayBuffer<T> {
    final int limit;

    final long maxAgeMillis;

    final Scheduler scheduler;

    volatile TimedNode<T> head;

    TimedNode<T> tail;

    int size;

    volatile boolean done;
    Throwable error;

    public ReplaySizeAndTimeBoundBuffer(int limit, long maxAgeMillis, Scheduler scheduler) {
        this.limit = limit;
        TimedNode<T> n = new TimedNode<T>(null, 0L);
        this.tail = n;
        this.head = n;
        this.maxAgeMillis = maxAgeMillis;
        this.scheduler = scheduler;
    }

    @Override
    public void next(T value) {
        long now = scheduler.now();

        TimedNode<T> n = new TimedNode<T>(value, now);
        tail.set(n);
        tail = n;

        now -= maxAgeMillis;

        int s = size;
        TimedNode<T> h0 = head;
        TimedNode<T> h = h0;

        if (s == limit) {
            h = h.get();
        } else {
            s++;
        }

        while ((n = h.get()) != null) {
            if (n.timestamp > now) {
                break;
            }
            h = n;
            s--;
        }

        size = s;
        if (h != h0) {
            head = h;
        }
    }

    @Override
    public void error(Throwable ex) {
        evictFinal();
        error = ex;
        done = true;
    }

    @Override
    public void complete() {
        evictFinal();
        done = true;
    }

    void evictFinal() {
        long now = scheduler.now() - maxAgeMillis;

        TimedNode<T> h0 = head;
        TimedNode<T> h = h0;
        TimedNode<T> n;

        while ((n = h.get()) != null) {
            if (n.timestamp > now) {
                break;
            }
            h = n;
        }

        if (h0 != h) {
            head = h;
        }
    }

    TimedNode<T> latestHead() {
        long now = scheduler.now() - maxAgeMillis;
        TimedNode<T> h = head;
        TimedNode<T> n;
        while ((n = h.get()) != null) {
            if (n.timestamp > now) {
                break;
            }
            h = n;
        }
        return h;
    }

    @Override
    public void drain(ReplayProducer<T> rp) {
        if (rp.getAndIncrement() != 0) {
            return;
        }

        final Subscriber<? super T> a = rp.actual;

        int missed = 1;

        for (; ; ) {

            long r = rp.requested.get();
            long e = 0L;

            @SuppressWarnings("unchecked")
            TimedNode<T> node = (TimedNode<T>) rp.node;
            if (node == null) {
                node = latestHead();
            }

            while (e != r) {
                if (a.isUnsubscribed()) {
                    rp.node = null;
                    return;
                }

                boolean d = done;
                TimedNode<T> next = node.get();
                boolean empty = next == null;

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

                a.onNext(next.value);

                e++;
                node = next;
            }

            if (e == r) {
                if (a.isUnsubscribed()) {
                    rp.node = null;
                    return;
                }

                boolean d = done;
                boolean empty = node.get() == null;

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

            rp.node = node;

            missed = rp.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    static final class TimedNode<T> extends AtomicReference<TimedNode<T>> {
        /** */
        private static final long serialVersionUID = 3713592843205853725L;

        final T value;

        final long timestamp;

        public TimedNode(T value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
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
        TimedNode<T> h = latestHead();
        TimedNode<T> n;
        while ((n = h.get()) != null) {
            h = n;
        }
        return h.value;
    }

    @Override
    public int size() {
        int s = 0;
        TimedNode<T> n = latestHead().get();
        while (n != null && s != Integer.MAX_VALUE) {
            n = n.get();
            s++;
        }
        return s;
    }

    @Override
    public boolean isEmpty() {
        return latestHead().get() == null;
    }

    @Override
    public T[] toArray(T[] a) {
        List<T> list = new ArrayList<T>();

        TimedNode<T> n = latestHead().get();
        while (n != null) {
            list.add(n.value);
            n = n.get();
        }
        return list.toArray(a);
    }

}
