package com.solutionarchitects.test;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.solutionarchitects.common.rx.v2.BehaviorSubject;
import com.solutionarchitects.common.rx.v2.ReplaySubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * Created by montu on 8/7/16.
 */
public class TestRx {


    private static Logger logger = LoggerFactory.getLogger(TestRx.class.getName());






    public static void main(String[] args) {


        Scheduler newThread = Schedulers.newThread();


        Scheduler myThread = Schedulers.from(Executors.newFixedThreadPool(1 ,
                new ThreadFactoryBuilder().setNameFormat("my-Thread-%d").build()));


        logger.info("-----------------------ReplaySubject--------------------------------");

        ReplaySubject<Object> replaySubject = ReplaySubject.create();

        Observable<Object> throttle = replaySubject.throttleWithTimeout(100, TimeUnit.MILLISECONDS).observeOn(myThread);


        Subscription subscription1 = throttle.subscribe(o -> {
            logger.info("Throttle Before data : {}", o);
        });


        Scheduler.Worker worker = newThread.createWorker();


        worker.schedule(() -> {

            logger.info("Replay Subject Publishing Data");
            replaySubject.onNext("one");
            replaySubject.onNext("two");
            replaySubject.onNext("three");

        });




        Subscription subscription2 = throttle.subscribe(o -> {
            logger.info("Throttle After Data : {}", o);
        });


        Func2<HashMap<String,Object>,HashMap<String,Object>,HashMap<String,Object>> agg = new Func2<HashMap<String, Object>, HashMap<String, Object>, HashMap<String, Object>>() {
            @Override
            public HashMap<String, Object> call(HashMap<String, Object> stringObjectHashMap, HashMap<String, Object> stringObjectHashMap2) {
                if(stringObjectHashMap != null) {
                    stringObjectHashMap.putAll(stringObjectHashMap2);
                    return stringObjectHashMap;
                }
                return stringObjectHashMap2;
            }
        };


        logger.info("-----------------------BehaviourSubject--------------------------------");

        BehaviorSubject<HashMap<String,Object>> behaviorSubject =  BehaviorSubject.create(agg);

        Subscription subscription3 = behaviorSubject.subscribe(o -> {

            if(o != null) {
                logger.info("Sub 1 Behaviour Subject Before Data : {}", o);
            }
        });

        HashMap<String , Object> data = new HashMap<>();

        data.put("Symbol", "AAPL");
        data.put("Bid", 100);
        data.put("Ask", 102);

        behaviorSubject.onNext(data);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        Observable<HashMap<String, Object>> throttle2 = behaviorSubject.throttleWithTimeout(100, TimeUnit.MILLISECONDS).observeOn(myThread);

        throttle2.subscribe(o -> {
            logger.info("Sub Throttle Behaviour Subject  Data : {}", o);
        });


        data = new HashMap<>();
        data.put("Symbol", "AAPL");
        data.put("Ask", 104);
        behaviorSubject.onNext(data);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        data = new HashMap<>();
        data.put("Symbol", "AAPL");
        data.put("Bid", 102);

        behaviorSubject.onNext(data);

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }



        Subscription subscription4 = behaviorSubject.subscribe(o -> {

            if(o != null) {
                logger.info("Sub 2 Behaviour Subject AFTER Data : {}", o);
            }
        });


        Subscription subscription5 = behaviorSubject.subscribe(o -> {

            if(o != null) {
                logger.info("Sub 3 Behaviour Subject AFTER Data : {}", o);
            }
        });





        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
