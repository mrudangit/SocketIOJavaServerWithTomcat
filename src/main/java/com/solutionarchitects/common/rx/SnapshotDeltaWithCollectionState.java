package com.solutionarchitects.common.rx;

import rx.functions.Func0;
import rx.functions.Func2;
import rx.internal.operators.NotificationLite;

/**
 * Created by e211303 on 3/29/2016.
 */
public class SnapshotDeltaWithCollectionState<T>  implements ReplayState<T, NodeList.Node<Object>> {

    private final Func2<Object, Object, Object> accumulator;
    private final Func0<Object> initialStateProvider;
    volatile boolean terminated;
    final NotificationLite<T> notificationLite = NotificationLite.instance();

    Object latestObject =null;


    public SnapshotDeltaWithCollectionState(Func0<Object> initialStateProvider, Func2<Object, Object, Object> accumulator) {

        this.initialStateProvider = initialStateProvider;
        this.accumulator = accumulator;
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
        if(latestObject != null) {
            notificationLite.accept(observer,latestObject );
            return true;
        }

        return false;
    }

    @Override
    public NodeList.Node<Object> replayObserverFromIndex(NodeList.Node<Object> idx, SubjectSubscriptionManager.SubjectObserver<? super T> observer) {
        return null;
    }

    @Override
    public NodeList.Node<Object> replayObserverFromIndexTest(NodeList.Node<Object> idx, SubjectSubscriptionManager.SubjectObserver<? super T> observer, long now) {
        return null;
    }

    @Override
    public void next(T value) {

        if(latestObject == null){
            latestObject = initialStateProvider.call();
            latestObject = accumulator.call(latestObject,value);
        }else{
            latestObject = accumulator.call(latestObject,value);
        }
        notificationLite.next(value);
    }

    @Override
    public void error(Throwable e) {

    }

    @Override
    public void complete() {
        if (!terminated) {
            terminated = true;
        }
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public T[] toArray(T[] a) {

        return null;
    }

    @Override
    public T latest() {
        return (T) latestObject;
    }
}
