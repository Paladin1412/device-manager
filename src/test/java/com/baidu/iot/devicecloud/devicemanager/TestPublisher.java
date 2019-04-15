package com.baidu.iot.devicecloud.devicemanager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.util.concurrent.Queues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestPublisher {
    private Cache<String, String> cache;

    private Callable<String> callable = () -> {
        Thread.sleep(5000);
        return "test";
    };

    @Before
    public void setup() {
        cache = CacheBuilder.newBuilder()
                .concurrencyLevel(10)
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .initialCapacity(10000)
                .maximumSize(10000000)
                .build();
    }

    @Test
    public void testMono() throws Exception {
        Mono.justOrEmpty(Optional.ofNullable(get()))
                .switchIfEmpty(Mono.just("default"))
        .subscribe(System.out::println);
    }

    private String get() {
        try {
            return cache.get("testKey", callable);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Test
    public void testWorkQueue() {
        UnicastProcessor<Integer> workQueue = UnicastProcessor.create(Queues.<Integer>xs().get());
        Flux flux = Flux.just(1, 2, 3);
        List<Integer> nums = new ArrayList<>();

        /*workQueue.onNext(1);
        workQueue.onNext(2);
        workQueue.onNext(3);*/

        nums.add(1);
        nums.add(2);
        nums.add(3);

        /*workQueue
                .collectList()
                .flatMapMany(list -> Flux.fromStream(list.stream().map(i -> i * i).collect(Collectors.toList()).stream()))
                .doOnNext(workQueue::onNext)
                .doFinally(signalType -> System.out.println(signalType.toString()))
                .subscribe(System.out::println);*/

        Flux.fromStream(nums.stream().map(i -> i * i).collect(Collectors.toList()).stream())
                .doOnNext(workQueue::onNext)
                .doFinally(signalType -> System.out.println(signalType.toString()))
                .subscribe(System.out::println);
    }

    @Test
    public void testEmptyList() {
        Flux.fromIterable(Collections.emptyList())
                .doFinally(signalType -> System.out.println(signalType.toString()))
                .subscribe(i -> System.out.println("received: " + i));
    }
}
