package com.solutionarchitects.common.rx.v2;

/**
 * Created by montu on 8/6/16.
 */



/**
 * The base interface for buffering signals to be replayed to individual
 * Subscribers.
 *
 * @param <T> the value type
 */
public interface ReplayBuffer<T> {

    void next(T t);

    void error(Throwable e);

    void complete();

    void drain(ReplayProducer<T> rp);

    boolean isComplete();

    Throwable error();

    T last();

    int size();

    boolean isEmpty();

    T[] toArray(T[] a);
}