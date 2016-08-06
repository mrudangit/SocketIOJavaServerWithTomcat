package com.solutionarchitects.rx;

/**
 * Created by e211303 on 3/24/2016.
 */

import rx.annotations.Beta;
import rx.exceptions.Exceptions;
import rx.internal.operators.NotificationLite;
import rx.subjects.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.Observer;

/**
 * Subject that, once an {@link Observer} has subscribed, emits all subsequently observed items to the
 * subscriber.
 * <p>
 * <img width="640" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/S.PublishSubject.png" alt="">
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code

PublishSubject<Object> subject = PublishSubject.create();
// observer1 will receive all onNext and onCompleted events
subject.subscribe(observer1);
subject.onNext("one");
subject.onNext("two");
// observer2 will only receive "three" and onCompleted
subject.subscribe(observer2);
subject.onNext("three");
subject.onCompleted();

} </pre>
 *
 * @param <T>
 *          the type of items observed and emitted by the Subject
 */
public final class PublishSubject<T> extends Subject<T, T> {

    /**
     * Creates and returns a new {@code PublishSubject}.
     *
     * @param <T> the value type
     * @return the new {@code PublishSubject}
     */
    public static <T> PublishSubject<T> create() {
        final SubjectSubscriptionManager<T> state = new SubjectSubscriptionManager<T>();
        state.onTerminated = o -> o.emitFirst(state.getLatest(), state.nl);
        return new PublishSubject<T>(state, state);
    }

    final SubjectSubscriptionManager<T> state;
    private final NotificationLite<T> nl = NotificationLite.instance();

    protected PublishSubject(OnSubscribe<T> onSubscribe, SubjectSubscriptionManager<T> state) {
        super(onSubscribe);
        this.state = state;
    }

    @Override
    public void onCompleted() {
        if (state.active) {
            Object n = nl.completed();
            for (SubjectSubscriptionManager.SubjectObserver<T> bo : state.terminate(n)) {
                bo.emitNext(n, state.nl);
            }
        }

    }

    @Override
    public void onError(final Throwable e) {
        if (state.active) {
            Object n = nl.error(e);
            List<Throwable> errors = null;
            for (SubjectSubscriptionManager.SubjectObserver<T> bo : state.terminate(n)) {
                try {
                    bo.emitNext(n, state.nl);
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
    public void onNext(T v) {
        for (SubjectSubscriptionManager.SubjectObserver<T> bo : state.observers()) {
            bo.onNext(v);
        }
    }

    @Override
    public boolean hasObservers() {
        return state.observers().length > 0;
    }

    /**
     * Check if the Subject has terminated with an exception.
     * @return true if the subject has received a throwable through {@code onError}.
     */
    @Beta
    public boolean hasThrowable() {
        Object o = state.getLatest();
        return nl.isError(o);
    }
    /**
     * Check if the Subject has terminated normally.
     * @return true if the subject completed normally via {@code onCompleted}
     */
    @Beta
    public boolean hasCompleted() {
        Object o = state.getLatest();
        return o != null && !nl.isError(o);
    }
    /**
     * Returns the Throwable that terminated the Subject.
     * @return the Throwable that terminated the Subject or {@code null} if the
     * subject hasn't terminated yet or it terminated normally.
     */
    @Beta
    public Throwable getThrowable() {
        Object o = state.getLatest();
        if (nl.isError(o)) {
            return nl.getError(o);
        }
        return null;
    }
}