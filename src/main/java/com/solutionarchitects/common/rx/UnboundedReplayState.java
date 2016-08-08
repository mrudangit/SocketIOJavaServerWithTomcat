package com.solutionarchitects.common.rx;

/**
 * Created by e211303 on 3/24/2016.
 */

import rx.Observer;
import rx.internal.operators.NotificationLite;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The unbounded replay state.
 * @param <T> the input and output type
 */
final class UnboundedReplayState<T> extends AtomicInteger implements ReplayState<T, Integer> {
    private final NotificationLite<T> nl = NotificationLite.instance();
    /** The buffer. */
    private final ArrayList<Object> list;
    /** The termination flag. */
    private volatile boolean terminated;
    public UnboundedReplayState(int initialCapacity) {
        list = new ArrayList<Object>(initialCapacity);
    }

    @Override
    public void next(T n) {
        if (!terminated) {
            list.add(nl.next(n));
            getAndIncrement(); // release index
        }
    }

    public void accept(Observer<? super T> o, int idx) {
        nl.accept(o, list.get(idx));
    }

    @Override
    public void complete() {
        if (!terminated) {
            terminated = true;
            list.add(nl.completed());
            getAndIncrement(); // release index
        }
    }
    @Override
    public void error(Throwable e) {
        if (!terminated) {
            terminated = true;
            list.add(nl.error(e));
            getAndIncrement(); // release index
        }
    }

    @Override
    public boolean terminated() {
        return terminated;
    }

    @Override
    public boolean replayObserver(SubjectSubscriptionManager.SubjectObserver<? super T> observer) {

        synchronized (observer) {
            observer.first = false;
            if (observer.emitting) {
                return false;
            }
        }

        Integer lastEmittedLink = observer.index();
        if (lastEmittedLink != null) {
            int l = replayObserverFromIndex(lastEmittedLink, observer);
            observer.index(l);
            return true;
        } else {
            throw new IllegalStateException("failed to find lastEmittedLink for: " + observer);
        }
    }

    @Override
    public Integer replayObserverFromIndex(Integer idx, SubjectSubscriptionManager.SubjectObserver<? super T> observer) {
        int i = idx;
        while (i < get()) {
            accept(observer, i);
            i++;
        }

        return i;
    }

    @Override
    public Integer replayObserverFromIndexTest(Integer idx, SubjectSubscriptionManager.SubjectObserver<? super T> observer, long now) {
        return replayObserverFromIndex(idx, observer);
    }

    @Override
    public int size() {
        int idx = get(); // aquire
        if (idx > 0) {
            Object o = list.get(idx - 1);
            if (nl.isCompleted(o) || nl.isError(o)) {
                return idx - 1; // do not report a terminal event as part of size
            }
        }
        return idx;
    }
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }
    @Override
    @SuppressWarnings("unchecked")
    public T[] toArray(T[] a) {
        int s = size();
        if (s > 0) {
            if (s > a.length) {
                a = (T[]) Array.newInstance(a.getClass().getComponentType(), s);
            }
            for (int i = 0; i < s; i++) {
                a[i] = (T)list.get(i);
            }
            if (a.length > s) {
                a[s] = null;
            }
        } else
        if (a.length > 0) {
            a[0] = null;
        }
        return a;
    }
    @Override
    public T latest() {
        int idx = get();
        if (idx > 0) {
            Object o = list.get(idx - 1);
            if (nl.isCompleted(o) || nl.isError(o)) {
                if (idx > 1) {
                    return nl.getValue(list.get(idx - 2));
                }
                return null;
            }
            return nl.getValue(o);
        }
        return null;
    }
}
