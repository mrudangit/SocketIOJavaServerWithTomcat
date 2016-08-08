package com.solutionarchitects.common.rx.v2;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.exceptions.Exceptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by montu on 8/6/16.
 */
class ReplayState<T>
        extends AtomicReference<ReplayProducer<T>[]>
        implements Observable.OnSubscribe<T>, Observer<T> {

    /** */
    private static final long serialVersionUID = 5952362471246910544L;

    final ReplayBuffer<T> buffer;

    @SuppressWarnings("rawtypes")
    static final ReplayProducer[] EMPTY = new ReplayProducer[0];
    @SuppressWarnings("rawtypes")
    static final ReplayProducer[] TERMINATED = new ReplayProducer[0];

    @SuppressWarnings("unchecked")
    public ReplayState(ReplayBuffer<T> buffer) {
        this.buffer = buffer;
        lazySet(EMPTY);
    }

    @Override
    public void call(Subscriber<? super T> t) {
        ReplayProducer<T> rp = new ReplayProducer<T>(t, this);
        t.add(rp);
        t.setProducer(rp);

        if (add(rp)) {
            if (rp.isUnsubscribed()) {
                remove(rp);
                return;
            }
        }
        buffer.drain(rp);
    }

    boolean add(ReplayProducer<T> rp) {
        for (; ; ) {
            ReplayProducer<T>[] a = get();
            if (a == TERMINATED) {
                return false;
            }

            int n = a.length;

            @SuppressWarnings("unchecked")
            ReplayProducer<T>[] b = new ReplayProducer[n + 1];
            System.arraycopy(a, 0, b, 0, n);
            b[n] = rp;

            if (compareAndSet(a, b)) {
                return true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    void remove(ReplayProducer<T> rp) {
        for (; ; ) {
            ReplayProducer<T>[] a = get();
            if (a == TERMINATED || a == EMPTY) {
                return;
            }

            int n = a.length;

            int j = -1;
            for (int i = 0; i < n; i++) {
                if (a[i] == rp) {
                    j = i;
                    break;
                }
            }

            if (j < 0) {
                return;
            }

            ReplayProducer<T>[] b;
            if (n == 1) {
                b = EMPTY;
            } else {
                b = new ReplayProducer[n - 1];
                System.arraycopy(a, 0, b, 0, j);
                System.arraycopy(a, j + 1, b, j, n - j - 1);
            }
            if (compareAndSet(a, b)) {
                return;
            }
        }
    }

    @Override
    public void onNext(T t) {
        ReplayBuffer<T> b = buffer;

        b.next(t);
        for (ReplayProducer<T> rp : get()) {
            b.drain(rp);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onError(Throwable e) {
        ReplayBuffer<T> b = buffer;

        b.error(e);
        List<Throwable> errors = null;
        for (ReplayProducer<T> rp : getAndSet(TERMINATED)) {
            try {
                b.drain(rp);
            } catch (Throwable ex) {
                if (errors == null) {
                    errors = new ArrayList<Throwable>();
                }
                errors.add(ex);
            }
        }

        Exceptions.throwIfAny(errors);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCompleted() {
        ReplayBuffer<T> b = buffer;

        b.complete();
        for (ReplayProducer<T> rp : getAndSet(TERMINATED)) {
            b.drain(rp);
        }
    }


    boolean isTerminated() {
        return get() == TERMINATED;
    }
}
