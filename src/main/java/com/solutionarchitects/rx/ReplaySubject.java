package com.solutionarchitects.rx;

/**
 * Created by e211303 on 3/24/2016.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.annotations.Beta;
import rx.exceptions.Exceptions;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.internal.operators.NotificationLite;
import rx.internal.util.UtilityFunctions;
import rx.schedulers.Timestamped;
import rx.subjects.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public  class ReplaySubject<T> extends Subject<T, T> {


    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());


    /**
     * Creates an unbounded replay subject.
     * <p>
     * The internal buffer is backed by an {@link ArrayList} and starts with an initial capacity of 16. Once the
     * number of items reaches this capacity, it will grow as necessary (usually by 50%). However, as the
     * number of items grows, this causes frequent array reallocation and copying, and may hurt performance
     * and latency. This can be avoided with the {@link #create(int)} overload which takes an initial capacity
     * parameter and can be tuned to reduce the array reallocation frequency as needed.
     *
     * @param <T>
     *          the type of items observed and emitted by the Subject
     * @return the created subject
     */
    public static <T> ReplaySubject<T> create() {
        return create(16);
    }

    /**
     * Creates an unbounded replay subject with the specified initial buffer capacity.
     * <p>
     * Use this method to avoid excessive array reallocation while the internal buffer grows to accomodate new
     * items. For example, if you know that the buffer will hold 32k items, you can ask the
     * {@code ReplaySubject} to preallocate its internal array with a capacity to hold that many items. Once
     * the items start to arrive, the internal array won't need to grow, creating less garbage and no overhead
     * due to frequent array-copying.
     *
     * @param <T>
     *          the type of items observed and emitted by the Subject
     * @param capacity
     *          the initial buffer capacity
     * @return the created subject
     */
    public static <T> ReplaySubject<T> create(int capacity) {
        final UnboundedReplayState<T> state = new UnboundedReplayState<T>(capacity);
        SubjectSubscriptionManager<T> ssm = new SubjectSubscriptionManager<T>();
        ssm.onStart = o -> {
            // replay history for this observer using the subscribing thread
            int lastIndex = state.replayObserverFromIndex(0, o);

            // now that it is caught up add to observers
            o.index(lastIndex);
        };
        ssm.onAdded = o -> {
            synchronized (o) {
                if (!o.first || o.emitting) {
                    return;
                }
                o.first = false;
                o.emitting = true;
            }
            boolean skipFinal = false;
            try {
                //noinspection UnnecessaryLocalVariable - Avoid re-read from outside this scope
                final UnboundedReplayState<T> localState = state;
                for (;;) {
                    int idx = o.<Integer>index();
                    int sidx = localState.get();
                    if (idx != sidx) {
                        Integer j = localState.replayObserverFromIndex(idx, o);
                        o.index(j);
                    }
                    synchronized (o) {
                        if (sidx == localState.get()) {
                            o.emitting = false;
                            skipFinal = true;
                            break;
                        }
                    }
                }
            } finally {
                if (!skipFinal) {
                    synchronized (o) {
                        o.emitting = false;
                    }
                }
            }
        };
        ssm.onTerminated = o -> {
            Integer idx = o.index();
            if (idx == null) {
                idx = 0;
            }
            // we will finish replaying if there is anything left
            state.replayObserverFromIndex(idx, o);
        };

        return new ReplaySubject<T>(ssm, ssm, state);
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
     * @param <T>
     *          the type of items observed and emitted by the Subject
     * @return the created subject
     */
    /* public */ static <T> ReplaySubject<T> createUnbounded() {
        final BoundedState<T> state = new BoundedState<T>(
                new EmptyEvictionPolicy(),
                UtilityFunctions.identity(),
                UtilityFunctions.identity()
        );
        return createWithState(state, new DefaultOnAdd<T>(state));
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
     * @param <T>
     *          the type of items observed and emitted by the Subject
     * @param size
     *          the maximum number of buffered items
     * @return the created subject
     */
    public static <T> ReplaySubject<T> createWithSize(int size) {
        final BoundedState<T> state = new BoundedState<T>(
                new SizeEvictionPolicy(size),
                UtilityFunctions.identity(),
                UtilityFunctions.identity()
        );
        return createWithState(state, new DefaultOnAdd<T>(state));
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
     * @param <T>
     *          the type of items observed and emitted by the Subject
     * @param time
     *          the maximum age of the contained items
     * @param unit
     *          the time unit of {@code time}
     * @param scheduler
     *          the {@link Scheduler} that provides the current time
     * @return the created subject
     */
    public static <T> ReplaySubject<T> createWithTime(long time, TimeUnit unit, final Scheduler scheduler) {
        final BoundedState<T> state = new BoundedState<T>(
                new TimeEvictionPolicy(unit.toMillis(time), scheduler),
                new AddTimestamped(scheduler),
                new RemoveTimestamped()
        );
        return createWithState(state, new TimedOnAdd<T>(state, scheduler));
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
     * @param <T>
     *          the type of items observed and emitted by the Subject
     * @param time
     *          the maximum age of the contained items
     * @param unit
     *          the time unit of {@code time}
     * @param size
     *          the maximum number of buffered items
     * @param scheduler
     *          the {@link Scheduler} that provides the current time
     * @return the created subject
     */
    public static <T> ReplaySubject<T> createWithTimeAndSize(long time, TimeUnit unit, int size, final Scheduler scheduler) {
        final BoundedState<T> state = new BoundedState<T>(
                new PairEvictionPolicy(
                        new SizeEvictionPolicy(size),
                        new TimeEvictionPolicy(unit.toMillis(time), scheduler)
                ),
                new AddTimestamped(scheduler),
                new RemoveTimestamped()
        );
        return createWithState(state, new TimedOnAdd<T>(state, scheduler));
    }
    /**
     * Creates a bounded replay subject with the given state shared between the subject and the
     * {@link OnSubscribe} functions.
     *
     * @param <T>
     *          the type of items observed and emitted by the Subject
     * @param state
     *          the shared state
     * @return the created subject
     */
    static <T> ReplaySubject<T> createWithState(final BoundedState<T> state,
                                                Action1<SubjectSubscriptionManager.SubjectObserver<T>> onStart) {
        SubjectSubscriptionManager<T> ssm = new SubjectSubscriptionManager<T>();
        ssm.onStart = onStart;
        ssm.onAdded = o -> {
            synchronized (o) {
                if (!o.first || o.emitting) {
                    return;
                }
                o.first = false;
                o.emitting = true;
            }
            boolean skipFinal = false;
            try {
                for (;;) {
                    NodeList.Node<Object> idx = o.index();
                    NodeList.Node<Object> sidx = state.tail();
                    if (idx != sidx) {
                        NodeList.Node<Object> j = state.replayObserverFromIndex(idx, o);
                        o.index(j);
                    }
                    synchronized (o) {
                        if (sidx == state.tail()) {
                            o.emitting = false;
                            skipFinal = true;
                            break;
                        }
                    }
                }
            } finally {
                if (!skipFinal) {
                    synchronized (o) {
                        o.emitting = false;
                    }
                }
            }
        };
        ssm.onTerminated = t1 -> {
            NodeList.Node<Object> l = t1.index();
            if (l == null) {
                l = state.head();
            }
            state.replayObserverFromIndex(l, t1);
        };

        return new ReplaySubject<T>(ssm, ssm, state);
    }






    public static <T> ReplaySubject<T> createWithSnapshotDeltaState(Func2<Object, Object, Object> accumulator) {
        final SnapshotDeltaState<T> state = new SnapshotDeltaState<T>(
                accumulator
        );
        return createWithSnapshotDeltaState(state);
    }


    protected static <T> ReplaySubject<T> createWithSnapshotDeltaState(final SnapshotDeltaState<T> state) {
        SubjectSubscriptionManager<T> ssm = new SubjectSubscriptionManager<T>();
        ssm.onStart = tSubjectObserver -> {
            state.replayObserver(tSubjectObserver);
            tSubjectObserver.caughtUp = true;
        };
        ssm.onAdded = tSubjectObserver -> {

        };
        ssm.onTerminated = t1 -> {
           System.out.println("SnapshotDelta State Terminated");
        };

        return new ReplaySubject<T>(ssm, ssm, state);
    }

    protected static <T> SubjectSubscriptionManager<T> createSubjectSubscriptionManagerWithSnapshotDelta(final SnapshotDeltaState<T> state) {
        SubjectSubscriptionManager<T> ssm = new SubjectSubscriptionManager<T>();
        ssm.onStart = tSubjectObserver -> {
            state.replayObserver(tSubjectObserver);
            tSubjectObserver.caughtUp = true;
        };
        ssm.onAdded = tSubjectObserver -> {

        };
        ssm.onTerminated = t1 -> {
            System.out.println("SnapshotDelta State Terminated");
        };

        return  ssm;
    }


    protected static <T> SubjectSubscriptionManager<T> createSubjectSubscriptionManagerWithSnapshotDeltaWithCollection(final SnapshotDeltaWithCollectionState<T> state) {
        SubjectSubscriptionManager<T> ssm = new SubjectSubscriptionManager<T>();
        ssm.onStart = tSubjectObserver -> {
            state.replayObserver(tSubjectObserver);
            tSubjectObserver.caughtUp = true;
        };
        ssm.onAdded = tSubjectObserver -> {

        };
        ssm.onTerminated = t1 -> {
            System.out.println("SnapshotDelta State Terminated");
        };

        return  ssm;
    }





    /** The state storing the history and the references. */
    final ReplayState<T, ?> state;
    /** The manager of subscribers. */
    final SubjectSubscriptionManager<T> ssm;
    protected ReplaySubject(OnSubscribe<T> onSubscribe, SubjectSubscriptionManager<T> ssm, ReplayState<T, ?> state) {
        super(onSubscribe);
        this.ssm = ssm;
        this.state = state;
    }

    @Override
    public void onNext(T t) {
        if (ssm.active) {
            state.next(t);
            for (SubjectSubscriptionManager.SubjectObserver<? super T> o : ssm.observers()) {
                if (caughtUp(o)) {
                    o.onNext(t);
                }
            }
        }
    }

    @Override
    public void onError(final Throwable e) {
        if (ssm.active) {
            state.error(e);
            List<Throwable> errors = null;
            for (SubjectSubscriptionManager.SubjectObserver<? super T> o : ssm.terminate(NotificationLite.instance().error(e))) {
                try {
                    if (caughtUp(o)) {
                        o.onError(e);
                    }
                } catch (Throwable e2) {
                    if (errors == null) {
                        errors = new ArrayList<Throwable>();
                    }
                    errors.add(e2);
                }
            }

            Exceptions.throwIfAny(errors);
        }
    }

    @Override
    public void onCompleted() {
        if (ssm.active) {
            state.complete();
            for (SubjectSubscriptionManager.SubjectObserver<? super T> o : ssm.terminate(NotificationLite.instance().completed())) {
                if (caughtUp(o)) {
                    o.onCompleted();
                }
            }
        }
    }
    /**
     * @return Returns the number of subscribers.
     */
    /* Support test. */int subscriberCount() {
        return ssm.get().observers.length;
    }

    @Override
    public boolean hasObservers() {
        return ssm.observers().length > 0;
    }

    private boolean caughtUp(SubjectSubscriptionManager.SubjectObserver<? super T> o) {
        if (!o.caughtUp) {
            if (state.replayObserver(o)) {
                o.caughtUp = true;
                o.index(null); // once caught up, no need for the index anymore
            }
            return false;
        } else {
            // it was caught up so proceed the "raw route"
            return true;
        }
    }


    //region Supporting Classes
    // ************************
    // Callback implementations
    // ************************

    /**
     * Remove elements from the beginning of the list if the size exceeds some threshold.
     */
    static final class SizeEvictionPolicy implements EvictionPolicy {
        final int maxSize;

        public SizeEvictionPolicy(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        public void evict(NodeList<Object> t1) {
            while (t1.size() > maxSize) {
                t1.removeFirst();
            }
        }

        @Override
        public boolean test(Object value, long now) {
            return false; // size gets never stale
        }

        @Override
        public void evictFinal(NodeList<Object> t1) {
            while (t1.size() > maxSize + 1) {
                t1.removeFirst();
            }
        }
    }
    /**
     * Remove elements from the beginning of the list if the Timestamped value is older than
     * a threshold.
     */
    static final class TimeEvictionPolicy implements EvictionPolicy {
        final long maxAgeMillis;
        final Scheduler scheduler;

        public TimeEvictionPolicy(long maxAgeMillis, Scheduler scheduler) {
            this.maxAgeMillis = maxAgeMillis;
            this.scheduler = scheduler;
        }

        @Override
        public void evict(NodeList<Object> t1) {
            long now = scheduler.now();
            while (!t1.isEmpty()) {
                NodeList.Node<Object> n = t1.head.next;
                if (test(n.value, now)) {
                    t1.removeFirst();
                } else {
                    break;
                }
            }
        }

        @Override
        public void evictFinal(NodeList<Object> t1) {
            long now = scheduler.now();
            while (t1.size > 1) {
                NodeList.Node<Object> n = t1.head.next;
                if (test(n.value, now)) {
                    t1.removeFirst();
                } else {
                    break;
                }
            }
        }

        @Override
        public boolean test(Object value, long now) {
            Timestamped<?> ts = (Timestamped<?>)value;
            return ts.getTimestampMillis() <= now - maxAgeMillis;
        }

    }
    /**
     * Pairs up two eviction policy callbacks.
     */
    static final class PairEvictionPolicy implements EvictionPolicy {
        final EvictionPolicy first;
        final EvictionPolicy second;

        public PairEvictionPolicy(EvictionPolicy first, EvictionPolicy second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void evict(NodeList<Object> t1) {
            first.evict(t1);
            second.evict(t1);
        }

        @Override
        public void evictFinal(NodeList<Object> t1) {
            first.evictFinal(t1);
            second.evictFinal(t1);
        }

        @Override
        public boolean test(Object value, long now) {
            return first.test(value, now) || second.test(value, now);
        }
    }

    /** Maps the values to Timestamped. */
    static final class AddTimestamped implements Func1<Object, Object> {
        final Scheduler scheduler;

        public AddTimestamped(Scheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public Object call(Object t1) {
            return new Timestamped<Object>(scheduler.now(), t1);
        }
    }
    /** Maps timestamped values back to raw objects. */
    static final class RemoveTimestamped implements Func1<Object, Object> {
        @Override
        @SuppressWarnings("unchecked")
        public Object call(Object t1) {
            return ((Timestamped<Object>)t1).getValue();
        }
    }
    /**
     * Default action of simply replaying the buffer on subscribe.
     * @param <T> the input and output value type
     */
    static final class DefaultOnAdd<T> implements Action1<SubjectSubscriptionManager.SubjectObserver<T>> {
        final BoundedState<T> state;

        public DefaultOnAdd(BoundedState<T> state) {
            this.state = state;
        }

        @Override
        public void call(SubjectSubscriptionManager.SubjectObserver<T> t1) {
            NodeList.Node<Object> l = state.replayObserverFromIndex(state.head(), t1);
            t1.index(l);
        }

    }


    static final class DefaultOnAddSnapshotDelta<T> implements Action1<SubjectSubscriptionManager.SubjectObserver<T>> {
        final SnapshotDeltaState<T> state;

        public DefaultOnAddSnapshotDelta(SnapshotDeltaState<T> state) {
            this.state = state;
        }

        @Override
        public void call(SubjectSubscriptionManager.SubjectObserver<T> t1) {

        }

    }

    /**
     * Action of replaying non-stale entries of the buffer on subscribe
     * @param <T> the input and output value
     */
    static final class TimedOnAdd<T> implements Action1<SubjectSubscriptionManager.SubjectObserver<T>> {
        final BoundedState<T> state;
        final Scheduler scheduler;

        public TimedOnAdd(BoundedState<T> state, Scheduler scheduler) {
            this.state = state;
            this.scheduler = scheduler;
        }

        @Override
        public void call(SubjectSubscriptionManager.SubjectObserver<T> t1) {
            NodeList.Node<Object> l;
            if (!state.terminated) {
                // ignore stale entries if still active
                l = state.replayObserverFromIndexTest(state.head(), t1, scheduler.now());
            }  else {
                // accept all if terminated
                l = state.replayObserverFromIndex(state.head(), t1);
            }
            t1.index(l);
        }

    }

    /** Empty eviction policy. */
    static final class EmptyEvictionPolicy implements EvictionPolicy {
        @Override
        public boolean test(Object value, long now) {
            return true;
        }
        @Override
        public void evict(NodeList<Object> list) {
        }
        @Override
        public void evictFinal(NodeList<Object> list) {
        }
    }

    //endregion


    /**
     * Check if the Subject has terminated with an exception.
     * @return true if the subject has received a throwable through {@code onError}.
     */
    @Beta
    public boolean hasThrowable() {
        NotificationLite<T> nl = ssm.nl;
        Object o = ssm.getLatest();
        return nl.isError(o);
    }
    /**
     * Check if the Subject has terminated normally.
     * @return true if the subject completed normally via {@code onCompleted}
     */
    @Beta
    public boolean hasCompleted() {
        NotificationLite<T> nl = ssm.nl;
        Object o = ssm.getLatest();
        return o != null && !nl.isError(o);
    }
    /**
     * Returns the Throwable that terminated the Subject.
     * @return the Throwable that terminated the Subject or {@code null} if the
     * subject hasn't terminated yet or it terminated normally.
     */
    @Beta
    public Throwable getThrowable() {
        NotificationLite<T> nl = ssm.nl;
        Object o = ssm.getLatest();
        if (nl.isError(o)) {
            return nl.getError(o);
        }
        return null;
    }
    /**
     * Returns the current number of items (non-terminal events) available for replay.
     * @return the number of items available
     */
    @Beta
    public int size() {
        return state.size();
    }
    /**
     * @return true if the Subject holds at least one non-terminal event available for replay
     */
    @Beta
    public boolean hasAnyValue() {
        return !state.isEmpty();
    }
    @Beta
    public boolean hasValue() {
        return hasAnyValue();
    }
    /**
     * Returns a snapshot of the currently buffered non-terminal events into
     * the provided {@code a} array or creates a new array if it has not enough capacity.
     * @param a the array to fill in
     * @return the array {@code a} if it had enough capacity or a new array containing the available values
     */
    @Beta
    public T[] getValues(T[] a) {
        return state.toArray(a);
    }

    /** An empty array to trigger getValues() to return a new array. */
    private static final Object[] EMPTY_ARRAY = new Object[0];

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
        T[] r = getValues((T[])EMPTY_ARRAY);
        if (r == EMPTY_ARRAY) {
            return new Object[0]; // don't leak the default empty array.
        }
        return r;
    }

    @Beta
    public T getValue() {
        return state.latest();
    }
}