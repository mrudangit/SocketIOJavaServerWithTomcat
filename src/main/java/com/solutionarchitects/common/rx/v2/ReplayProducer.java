package com.solutionarchitects.common.rx.v2;

/**
 * Created by montu on 8/6/16.
 */


import rx.Producer;
import rx.Subscriber;
import rx.Subscription;
import rx.internal.operators.BackpressureUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A producer and subscription implementation that tracks the current
 * replay position of a particular subscriber.
 * <p>
 * The this holds the current work-in-progress indicator used by serializing
 * replays.
 *
 * @param <T> the value type
 */
final class ReplayProducer<T>
        extends AtomicInteger
        implements Producer, Subscription {
    /** */
    private static final long serialVersionUID = -5006209596735204567L;

    /**
     * The wrapped Subscriber instance.
     */
    final Subscriber<? super T> actual;

    /**
     * Holds the current requested amount.
     */
    final AtomicLong requested;

    /**
     * Holds the back-reference to the replay state object.
     */
    final ReplayState<T> state;

    /**
     * Unbounded buffer.drain() uses this field to remember the absolute index of
     * values replayed to this Subscriber.
     */
    int index;

    /**
     * Unbounded buffer.drain() uses this index within its current node to indicate
     * how many items were replayed from that particular node so far.
     */
    int tailIndex;

    /**
     * Stores the current replay node of the buffer to be used by buffer.drain().
     */
    Object node;

    public ReplayProducer(Subscriber<? super T> actual, ReplayState<T> state) {
        this.actual = actual;
        this.requested = new AtomicLong();
        this.state = state;
    }

    @Override
    public void unsubscribe() {
        state.remove(this);
    }

    @Override
    public boolean isUnsubscribed() {
        return actual.isUnsubscribed();
    }

    @Override
    public void request(long n) {
        if (n > 0L) {
            BackpressureUtils.getAndAddRequest(requested, n);
            state.buffer.drain(this);
        } else if (n < 0L) {
            throw new IllegalArgumentException("n >= required but it was " + n);
        }
    }
}