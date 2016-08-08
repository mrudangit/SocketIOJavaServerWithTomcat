package com.solutionarchitects.common.rx.v2;


import java.util.*;
import java.util.concurrent.TimeUnit;

import rx.*;
import rx.Observer;
import rx.annotations.Beta;
import rx.schedulers.Schedulers;
import rx.subjects.Subject;

/**
 * Subject that buffers all items it observes and replays them to any {@link Observer} that subscribes.
 * <p>
 * <img width="640" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/S.ReplaySubject.png" alt="">
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code
 *
 * ReplaySubject<Object> subject = ReplaySubject.create();
 * subject.onNext("one");
 * subject.onNext("two");
 * subject.onNext("three");
 * subject.onCompleted();
 *
 * // both of the following will get the onNext/onCompleted calls from above
 * subject.subscribe(observer1);
 * subject.subscribe(observer2);
 *
 * } </pre>
 *
 * @param <T> the type of items observed and emitted by the Subject
 */
public final class ReplaySubject<T> extends Subject<T, T> {
    /**
     * The state storing the history and the references.
     */
    final ReplayState<T> state;
    /**
     * An empty array to trigger getValues() to return a new array.
     */
    private static final Object[] EMPTY_ARRAY = new Object[0];

    /**
     * Creates an unbounded replay subject.
     * <p>
     * The internal buffer is backed by an {@link ArrayList} and starts with an initial capacity of 16. Once the
     * number of items reaches this capacity, it will grow as necessary (usually by 50%). However, as the
     * number of items grows, this causes frequent array reallocation and copying, and may hurt performance
     * and latency. This can be avoided with the {@link #create(int)} overload which takes an initial capacity
     * parameter and can be tuned to reduce the array reallocation frequency as needed.
     *
     * @param <T> the type of items observed and emitted by the Subject
     * @return the created subject
     */
    public static <T> ReplaySubject<T> create() {
        return create(16);
    }

    /**
     * Creates an unbounded replay subject with the specified initial buffer capacity.
     * <p>
     * Use this method to avoid excessive array reallocation while the internal buffer grows to accommodate new
     * items. For example, if you know that the buffer will hold 32k items, you can ask the
     * {@code ReplaySubject} to preallocate its internal array with a capacity to hold that many items. Once
     * the items start to arrive, the internal array won't need to grow, creating less garbage and no overhead
     * due to frequent array-copying.
     *
     * @param <T>      the type of items observed and emitted by the Subject
     * @param capacity the initial buffer capacity
     * @return the created subject
     */
    public static <T> ReplaySubject<T> create(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity > 0 required but it was " + capacity);
        }
        ReplayBuffer<T> buffer = new ReplayUnboundedBuffer<T>(capacity);
        ReplayState<T> state = new ReplayState<T>(buffer);
        return new ReplaySubject<T>(state);
    }

    /**
     * Creates an unbounded replay subject with the bounded-implementation for testing purposes.
     * <p>
     * This variant behaves like the regular unbounded {@code ReplaySubject} created via {@link #create()} but
     * uses the structures of the bounded-implementation. This is by no means intended for the replacement of
     * the original, array-backed and unbounded {@code ReplaySubject} due to the additional overhead of the
     * linked-list based internal buffer. The sole purpose is to allow testing and reasoning about the behavior
     * of the bounded implementations without the interference of the eviction policies.
     *
     * @param <T> the type of items observed and emitted by the Subject
     * @return the created subject
     */
    /* public */
    static <T> ReplaySubject<T> createUnbounded() {
        ReplayBuffer<T> buffer = new ReplaySizeBoundBuffer<T>(Integer.MAX_VALUE);
        ReplayState<T> state = new ReplayState<T>(buffer);
        return new ReplaySubject<T>(state);
    }

    /**
     * Creates an unbounded replay subject with the time-bounded-implementation for testing purposes.
     * <p>
     * This variant behaves like the regular unbounded {@code ReplaySubject} created via {@link #create()} but
     * uses the structures of the bounded-implementation. This is by no means intended for the replacement of
     * the original, array-backed and unbounded {@code ReplaySubject} due to the additional overhead of the
     * linked-list based internal buffer. The sole purpose is to allow testing and reasoning about the behavior
     * of the bounded implementations without the interference of the eviction policies.
     *
     * @param <T> the type of items observed and emitted by the Subject
     * @return the created subject
     */
    /* public */
    static <T> ReplaySubject<T> createUnboundedTime() {
        ReplayBuffer<T> buffer = new ReplaySizeAndTimeBoundBuffer<T>(Integer.MAX_VALUE, Long.MAX_VALUE, Schedulers.immediate());
        ReplayState<T> state = new ReplayState<T>(buffer);
        return new ReplaySubject<T>(state);
    }

    /**
     * Creates a size-bounded replay subject.
     * <p>
     * In this setting, the {@code ReplaySubject} holds at most {@code size} items in its internal buffer and
     * discards the oldest item.
     * <p>
     * When observers subscribe to a terminated {@code ReplaySubject}, they are guaranteed to see at most
     * {@code size} {@code onNext} events followed by a termination event.
     * <p>
     * If an observer subscribes while the {@code ReplaySubject} is active, it will observe all items in the
     * buffer at that point in time and each item observed afterwards, even if the buffer evicts items due to
     * the size constraint in the mean time. In other words, once an Observer subscribes, it will receive items
     * without gaps in the sequence.
     *
     * @param <T>  the type of items observed and emitted by the Subject
     * @param size the maximum number of buffered items
     * @return the created subject
     */
    public static <T> ReplaySubject<T> createWithSize(int size) {
        ReplayBuffer<T> buffer = new ReplaySizeBoundBuffer<T>(size);
        ReplayState<T> state = new ReplayState<T>(buffer);
        return new ReplaySubject<T>(state);
    }

    /**
     * Creates a time-bounded replay subject.
     * <p>
     * In this setting, the {@code ReplaySubject} internally tags each observed item with a timestamp value
     * supplied by the {@link Scheduler} and keeps only those whose age is less than the supplied time value
     * converted to milliseconds. For example, an item arrives at T=0 and the max age is set to 5; at T&gt;=5
     * this first item is then evicted by any subsequent item or termination event, leaving the buffer empty.
     * <p>
     * Once the subject is terminated, observers subscribing to it will receive items that remained in the
     * buffer after the terminal event, regardless of their age.
     * <p>
     * If an observer subscribes while the {@code ReplaySubject} is active, it will observe only those items
     * from within the buffer that have an age less than the specified time, and each item observed thereafter,
     * even if the buffer evicts items due to the time constraint in the mean time. In other words, once an
     * observer subscribes, it observes items without gaps in the sequence except for any outdated items at the
     * beginning of the sequence.
     * <p>
     * Note that terminal notifications ({@code onError} and {@code onCompleted}) trigger eviction as well. For
     * example, with a max age of 5, the first item is observed at T=0, then an {@code onCompleted} notification
     * arrives at T=10. If an observer subscribes at T=11, it will find an empty {@code ReplaySubject} with just
     * an {@code onCompleted} notification.
     *
     * @param <T>       the type of items observed and emitted by the Subject
     * @param time      the maximum age of the contained items
     * @param unit      the time unit of {@code time}
     * @param scheduler the {@link Scheduler} that provides the current time
     * @return the created subject
     */
    public static <T> ReplaySubject<T> createWithTime(long time, TimeUnit unit, final Scheduler scheduler) {
        return createWithTimeAndSize(time, unit, Integer.MAX_VALUE, scheduler);
    }

    /**
     * Creates a time- and size-bounded replay subject.
     * <p>
     * In this setting, the {@code ReplaySubject} internally tags each received item with a timestamp value
     * supplied by the {@link Scheduler} and holds at most {@code size} items in its internal buffer. It evicts
     * items from the start of the buffer if their age becomes less-than or equal to the supplied age in
     * milliseconds or the buffer reaches its {@code size} limit.
     * <p>
     * When observers subscribe to a terminated {@code ReplaySubject}, they observe the items that remained in
     * the buffer after the terminal notification, regardless of their age, but at most {@code size} items.
     * <p>
     * If an observer subscribes while the {@code ReplaySubject} is active, it will observe only those items
     * from within the buffer that have age less than the specified time and each subsequent item, even if the
     * buffer evicts items due to the time constraint in the mean time. In other words, once an observer
     * subscribes, it observes items without gaps in the sequence except for the outdated items at the beginning
     * of the sequence.
     * <p>
     * Note that terminal notifications ({@code onError} and {@code onCompleted}) trigger eviction as well. For
     * example, with a max age of 5, the first item is observed at T=0, then an {@code onCompleted} notification
     * arrives at T=10. If an observer subscribes at T=11, it will find an empty {@code ReplaySubject} with just
     * an {@code onCompleted} notification.
     *
     * @param <T>       the type of items observed and emitted by the Subject
     * @param time      the maximum age of the contained items
     * @param unit      the time unit of {@code time}
     * @param size      the maximum number of buffered items
     * @param scheduler the {@link Scheduler} that provides the current time
     * @return the created subject
     */
    public static <T> ReplaySubject<T> createWithTimeAndSize(long time, TimeUnit unit, int size, final Scheduler scheduler) {
        ReplayBuffer<T> buffer = new ReplaySizeAndTimeBoundBuffer<T>(size, unit.toMillis(time), scheduler);
        ReplayState<T> state = new ReplayState<T>(buffer);
        return new ReplaySubject<T>(state);
    }

    ReplaySubject(ReplayState<T> state) {
        super(state);
        this.state = state;
    }

    @Override
    public void onNext(T t) {
        state.onNext(t);
    }

    @Override
    public void onError(final Throwable e) {
        state.onError(e);
    }

    @Override
    public void onCompleted() {
        state.onCompleted();
    }

    /**
     * @return Returns the number of subscribers.
     */
    /* Support test. */int subscriberCount() {
        return state.get().length;
    }

    @Override
    public boolean hasObservers() {
        return state.get().length != 0;
    }

    /**
     * Check if the Subject has terminated with an exception.
     *
     * @return true if the subject has received a throwable through {@code onError}.
     */
    @Beta
    public boolean hasThrowable() {
        return state.isTerminated() && state.buffer.error() != null;
    }

    /**
     * Check if the Subject has terminated normally.
     *
     * @return true if the subject completed normally via {@code onCompleted}
     */
    @Beta
    public boolean hasCompleted() {
        return state.isTerminated() && state.buffer.error() == null;
    }

    /**
     * Returns the Throwable that terminated the Subject.
     *
     * @return the Throwable that terminated the Subject or {@code null} if the
     * subject hasn't terminated yet or it terminated normally.
     */
    @Beta
    public Throwable getThrowable() {
        if (state.isTerminated()) {
            return state.buffer.error();
        }
        return null;
    }

    /**
     * Returns the current number of items (non-terminal events) available for replay.
     *
     * @return the number of items available
     */
    @Beta
    public int size() {
        return state.buffer.size();
    }

    /**
     * @return true if the Subject holds at least one non-terminal event available for replay
     */
    @Beta
    public boolean hasAnyValue() {
        return !state.buffer.isEmpty();
    }

    @Beta
    public boolean hasValue() {
        return hasAnyValue();
    }

    /**
     * Returns a snapshot of the currently buffered non-terminal events into
     * the provided {@code a} array or creates a new array if it has not enough capacity.
     *
     * @param a the array to fill in
     * @return the array {@code a} if it had enough capacity or a new array containing the available values
     */
    @Beta
    public T[] getValues(T[] a) {
        return state.buffer.toArray(a);
    }

    /**
     * Returns a snapshot of the currently buffered non-terminal events.
     * <p>The operation is threadsafe.
     *
     * @return a snapshot of the currently buffered non-terminal events.
     * @since (If this graduates from being an Experimental class method, replace this parenthetical with the release number)
     */
    @SuppressWarnings("unchecked")
    @Beta
    public Object[] getValues() {
        T[] r = getValues((T[]) EMPTY_ARRAY);
        if (r == EMPTY_ARRAY) {
            return new Object[0]; // don't leak the default empty array.
        }
        return r;
    }

    @Beta
    public T getValue() {
        return state.buffer.last();
    }







}