package com.baidu.iot.devicecloud.devicemanager;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.channels.Channel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/5/14.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestDataBuffer {
    private final Logger logger = LoggerFactory.getLogger(TestDataBuffer.class);
    private InputStream in;
    private DataBufferFactory dataBufferFactory;
    private Map<String, Channel> channels;

    private volatile boolean finished;

    @Before
    public void setup() {
        dataBufferFactory = new DefaultDataBufferFactory();
        try {
            in = new FileInputStream(new File("E:/track.json"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        channels = null;
    }

    @Test
    public void test() {
        Mono.from(readAs0(in))
                .doOnNext(dataBuffer -> {
                    System.out.println(dataBuffer.readableByteCount());
                    System.out.println(dataBuffer.readPosition());
                    System.out.println(dataBuffer.writePosition());
                })
                .subscribe();
    }

    private Mono<DataBuffer> readAs0(InputStream in) {
        Flux<DataBuffer> flux = DataBufferUtils.readInputStream(
                () -> in,
                dataBufferFactory,
                512
        );
        return DataBufferUtils.join(flux);
    }

    @Test
    public void testRead() {
        Flux<DataBuffer> flux = DataBufferUtils.readInputStream(
                () -> in,
                dataBufferFactory,
                512
        );
        flux.doOnNext(dataBuffer -> {
            System.out.println(dataBuffer.readableByteCount());
            System.out.println(dataBuffer.readPosition());
            System.out.println(dataBuffer.writePosition());
        }).
                subscribe();
    }

    @Test
    public void testTryNull() {
        try(Channel channel = channels.get("a")) {

        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        DataBuffer dataBuffer = dataBufferFactory.allocateBuffer();
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                String str = "This is test " + i;
                dataBuffer.write(str.getBytes());
                logger.info("The writer thread has written: {}", str);
                try {
                    logger.info("Writer waits for 100ms");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            finished = true;
        });
        Thread reader = new Thread(() -> read(dataBuffer)
                .doOnNext(dataBuffer1 -> {
                    byte[] dest = new byte[dataBuffer1.readableByteCount()];
                    dataBuffer1.read(dest);
                    logger.info("Read: {}", new String(dest, StandardCharsets.UTF_8));
//                    logger.info("{}:{}", dataBuffer.readPosition(), dataBuffer.writePosition());
                })
                .subscribe());

        Thread reader1 = new Thread(() -> {
            while (!finished) {
                logger.info("[READ] Waiting more data");
                if (dataBuffer.readableByteCount() > 0) {
                    byte[] dest = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(dest);
                    logger.info("Read: {}", new String(dest, StandardCharsets.UTF_8));
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (dataBuffer.readableByteCount() > 0) {
                byte[] dest = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(dest);
                logger.info("Read: {}", new String(dest, StandardCharsets.UTF_8));
            }
        });
        reader1.start();
        Thread.sleep(50);
        writer.start();
        reader.join();
        writer.join();
    }

    public Flux<DataBuffer> read(DataBuffer buffer) {
        return Flux.<DataBuffer>push(sink -> {
            if (finished) {
                if (buffer.readPosition() != 0) {
                    buffer.readPosition(0);
                }
                int batches = buffer.readableByteCount() / 10;
                if (buffer.readableByteCount() % 10 > 0) {
                    batches++;
                }

                for (int i = 0; i < batches; i++) {
                    int start = i * 10;
                    sink.next(buffer.slice(start, Math.min(buffer.capacity() - start, 10)));
                }
            } else {
                logger.info("[READ] Waiting more data");
                while (!finished) {
                    DataBuffer sliced = readSlice(buffer);
                    if (sliced != null) {
                        sink.next(sliced);
                    }
                }
                logger.info("[READ] Waiting finished");
                DataBuffer rest = readSlice(buffer);
                if (rest != null) {
                    sink.next(rest);
                }
            }
            sink.complete();
        })
                .elapsed()
                .flatMap(t -> {
                    logger.info("[READ] Elapsed time: {}ms", t.getT1());
                    return Mono.just(t.getT2());
                })
                .timeout(Duration.ofMillis(5000))
                .doFinally(signalType -> {
                    logger.info("[READ] Double check to finish the writer. signalType={}", signalType);
                    finished = true;
                });
    }

    private DataBuffer readSlice(DataBuffer buffer) {
        DataBuffer sliced = null;
        int readable = buffer.readableByteCount();
        if (readable > 0) {
            int readPos = buffer.readPosition();
            sliced = buffer.slice(readPos, readable);
            buffer.readPosition(readPos + readable);
        }
        return sliced;
    }
}
