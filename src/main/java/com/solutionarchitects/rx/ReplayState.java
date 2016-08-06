package com.solutionarchitects.rx;

/**
 * Created by e211303 on 3/24/2016.
 */
// **************
// API interfaces
// **************

/**
 * General API for replay state management.
 * @param <T> the input and output type
 * @param <I> the index type
 */
public interface ReplayState<T, I> {
    /** @return true if the subject has reached a terminal state. */
    boolean terminated();
    /**
     * Replay contents to the given observer.
     * @param observer the receiver of events
     * @return true if the subject has caught up
     */
    boolean replayObserver(SubjectSubscriptionManager.SubjectObserver<? super T> observer);
    /**
     * Replay the buffered values from an index position and return a new index
     * @param idx the current index position
     * @param observer the receiver of events
     * @return the new index position
     */
    I replayObserverFromIndex(
            I idx, SubjectSubscriptionManager.SubjectObserver<? super T> observer);
    /**
     * Replay the buffered values from an index position while testing for stale entries and return a new index
     * @param idx the current index position
     * @param observer the receiver of events
     * @return the new index position
     */
    I replayObserverFromIndexTest(
            I idx, SubjectSubscriptionManager.SubjectObserver<? super T> observer, long now);
    /**
     * Add an OnNext value to the buffer
     * @param value the value to add
     */
    void next(T value);
    /**
     * Add an OnError exception and terminate the subject
     * @param e the exception to add
     */
    void error(Throwable e);
    /**
     * Add an OnCompleted exception and terminate the subject
     */
    void complete();
    /**
     * @return the number of non-terminal values in the replay buffer.
     */
    int size();
    /**
     * @return true if the replay buffer is empty of non-terminal values
     */
    boolean isEmpty();

    /**
     * Copy the current values (minus any terminal value) from the buffer into the array
     * or create a new array if there isn't enough room.
     * @param a the array to fill in
     * @return the array or a new array containing the current values
     */
    T[] toArray(T[] a);
    /**
     * Returns the latest value that has been buffered or null if no such value
     * present.
     * @return the latest value buffered or null if none
     */
    T latest();
}
