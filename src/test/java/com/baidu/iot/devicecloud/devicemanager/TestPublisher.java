package com.baidu.iot.devicecloud.devicemanager;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.primitives.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestPublisher {
    private Logger log = LoggerFactory.getLogger(TestPublisher.class);
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
        UnicastProcessor<Integer> workQueue1 = UnicastProcessor.create(Queues.<Integer>xs().get());
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

        workQueue.subscribe(integer -> {
                    workQueue1.onNext(integer);
            if (integer == 4) {
                System.out.println("Completing workQueue.subscribe");
//                workQueue.onComplete();
//                throw new RuntimeException("comp");
                workQueue1.onComplete();
                workQueue.cancel();
            }
            System.out.println("workQueue.subscribe: " + integer);
        },
                Throwable::printStackTrace,
                () -> System.out.println("completed"));
        System.out.println(workQueue.isTerminated());
        System.out.println(workQueue.isDisposed());

        workQueue1.subscribe(integer -> System.out.println("workQueue1.subscribe: " + integer),
                Throwable::printStackTrace,
                () -> System.out.println("completed1"));
    }

    @Test
    public void testEmptyList() {
        Flux.fromIterable(Collections.emptyList())
                .doFinally(signalType -> System.out.println(signalType.toString()))
                .subscribe(i -> System.out.println("received: " + i));
    }

    @Test
    public void testFlux2Mono() {
        Flux flux = Flux.just(1, 2, 3);
        Mono mono = Flux.push(fluxSink -> flux.subscribe(new BaseSubscriber<Integer>() {
            @Override
            protected void hookOnNext(Integer value) {
                fluxSink.next(value);
            }

            @Override
            protected void hookOnComplete() {
                fluxSink.complete();
            }
        })).collectList()
                .map(list -> {
                    list.add(4);
                    return list;
                })
        .doFinally(signalType -> System.out.println("Finally"))
        .switchIfEmpty(Mono.empty());

        mono.subscribe(System.out::println);
    }

    @Test
    public void testFilterList() {
        List<Integer> list = new ArrayList<>(Arrays.asList(1,2,3,4,5,6,7,8,9,10));
        list.removeIf(number -> number%2 == 0);
        System.out.println(list);
    }

    @Test
    public void testSchedulers() {
        Flux<Integer> flux = Flux.just(1, 2, 3);
        System.out.println(getResult(flux));
    }

    private String getResult(Flux<Integer> flux) {
        flux.publishOn(Schedulers.newSingle("single"))
                .doOnNext(i -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("now "+i);
                })
                .doFinally(System.out::println)
                .subscribe();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "here";
    }

    @Test
    public void testSchedulers1() {
        Flux<Integer> flux = Flux.just(1, 2, 3);
        flux.publishOn(Schedulers.newParallel("new single"))
                .flatMap(i -> {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    log.info("{}", i);
                    return Mono.just(i * i);
                })
                .doOnNext(i -> log.info("again {}", i))
                .log("testSchedulers1")
                .subscribe();


    }

    @Test
    public void testParallel() {
        Flux.range(0, 10)
                .publishOn(Schedulers.newSingle("single"))
                .flatMap(i -> Mono.just(longTimeService(i)).then())
                .then(Mono.just("here"))
                .log("testParallel")
                .doFinally(System.out::println)
                .subscribe();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Integer longTimeService(int i) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("{}", i);
        return i * i;
    }

    @Test
    public void testParallel1() {
        Flux.range(0, 10)
                .parallel()
                .runOn(Schedulers.parallel())
                .doOnNext(i -> log.info("again {}", i))
                .sequential()
                .subscribe();
    }

    @Test
    public void testFlatMap() {
        Flux<Integer> flux = Flux.just(1, 2, 3);
        flux.flatMap(i -> Flux.just(i, i * i))
                .subscribe(System.out::println);
    }

    @Test
    public void testBuffer() {
//        Flux.range(1, 10).bufferUntil(i -> i % 2 == 0).subscribe(System.out::println);
        Flux.fromArray(new Integer[] {1, 1, 1, 2, 2, 3})
                .groupBy(integer -> integer)
                .flatMapSequential(Flux::collectList)
                .flatMap(list -> {
                    Optional<Integer> merged = list.stream().reduce(Integer::sum);
                    return merged.<Publisher<Integer>>map(Flux::just).orElseGet(Flux::empty);
                })
                .subscribe(System.out::println);
    }

    @Test
    public void testGroup() {
        Flux
                .just(
                        demo(1, "hello"),
                        demo(1, "hello"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(2, "Gale"),
                        demo(3, "Yao")
                )
                .groupBy(TlvMessage::getType)
                .flatMapSequential(Flux::collectList)
                .flatMap(list -> {
                    Optional<TlvMessage> merged = merge(list);
                    return merged.<Publisher<? extends TlvMessage>>map(Flux::just).orElseGet(Flux::empty);
                })
                .subscribe(message -> System.out.println(message.getLength()));
    }

    @Test
    public void testGroup1() {
        Flux
                .just(
                        demo(1, "hello"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(3, "Yao")
                )
                .groupBy(TlvMessage::getType)
                .doOnNext(group -> {
                    System.out.println(group.key());
                    group.doOnNext(tlv -> System.out.println(String.format("group(%s): %d", group.key(), tlv.getType()))).subscribe();
                })
                .subscribe();
    }

    @Test
    public void testGroup2() {
        Flux
                .just(
                        demo(1, "hello"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(3, "Yao")
                )
                .groupBy(TlvMessage::getType)
                .doOnNext(group -> {
                    int key = group.key();
                    if (key > 1) {
                        group
                                .log("ssss")
                                .flatMapSequential(tlv -> {
                                    if (tlv.getType() == 2) {
                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    return Mono.just(tlv);
                                })
                                .subscribeOn(Schedulers.single())
                        .subscribe(tlv -> System.out.println(String.format("group(%s): %d", group.key(), tlv.getType())));
                    } else
                    group
                    .log("main")
                            .doOnNext(tlv -> {

                                System.out.println(String.format("groups(%s): %d", group.key(), tlv.getType()));
                            }).subscribe();
                })
                .subscribe();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGroup3() {
        Flux
                .just(
                        demo(1, "hello"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(3, "Yao")
                )
                .groupBy(TlvMessage::getType)
                .flatMapSequential(group -> {
                    System.out.println(group.key());
                    return group.flatMapSequential(Mono::just);
                })
                .log("main")
                .subscribe();
    }

    @Test
    public void testGroup4() {
        Flux
                .just(
                        demo(1, "hello"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(1, "hello"),
                        demo(2, "Gale"),
                        demo(3, "Yao")
                )
                .groupBy(tlv -> tlv.getType() > 1)
                .flatMapSequential(group -> {
                    System.out.println(group.key());
                    return group.flatMapSequential(Mono::just);
                })
                .log("main")
                .subscribe();
    }

    @Test
    public void testSubscribe() {
        Flux empty = Flux.push(fluxSink -> {
            for (int i = 0; i < 10; i++) {
                fluxSink.next(Flux.empty());
            }
            fluxSink.next(Flux.just("last"));
            fluxSink.complete();
        });

        Flux<String> concated = empty.concatWith(Mono.just("concat"));
        concated
                .log("aaa")
                .doFinally(System.out::println)
                .subscribe();
    }

    private static TlvMessage demo(int type, String s) {
        if (StringUtils.isEmpty(s)) {
            s = "";
        }
        byte[] bytes = s.getBytes();
        return new TlvMessage(type, bytes.length, bytes);
    }

    private Optional<TlvMessage> merge(List<TlvMessage> messages) {
        return messages.stream()
                .reduce(
                        (t1, t2) -> {
                            t1.setLength(t1.getLength() + t2.getLength());
                            BinaryNode v1 = t1.getValue();
                            BinaryNode v2 = t2.getValue();
                            byte[] b1 = v1.binaryValue();
                            byte[] b2 = v2.binaryValue();
                            byte[] b0 = Bytes.concat(b1, b2);
                            t1.setValue(new BinaryNode(b0));
                            return t1;
                        }
                );
    }
}
