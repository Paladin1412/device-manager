package com.baidu.iot.devicecloud.devicemanager.client.tcp;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvDecoder;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvEncoder;
import com.baidu.iot.devicecloud.devicemanager.handler.tcp.TlvOverTcpHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TlvOverTcpClient implements Runnable {
    private final InetSocketAddress address;
    private final TlvOverTcpHandler handler = new TlvOverTcpHandler();
    private boolean isRunning = false;
    private ExecutorService executor = null;

    public TlvOverTcpClient(InetSocketAddress address) {
        this.address = address;
    }

    public synchronized void startClient() {
        if (!isRunning) {
            executor = Executors.newFixedThreadPool(1);
            executor.execute(this);
            isRunning = true;
        }
    }

    public synchronized boolean stopClient() {
        boolean bReturn = true;
        if (isRunning) {
            if (executor != null) {
                executor.shutdown();
                try {
                    executor.shutdownNow();
                    if (executor.awaitTermination(calcTime(10, 0.66667), TimeUnit.SECONDS)) {
                        if (!executor.awaitTermination(calcTime(10, 0.33334), TimeUnit.SECONDS)) {
                            bReturn = false;
                        }
                    }
                } catch (InterruptedException ie) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                } finally {
                    executor = null;
                }
            }
            isRunning = false;
        }
        return bReturn;
    }

    private long calcTime(int nTime, double dValue) {
        return (long) ((double) nTime * dValue);
    }

    @Override
    public void run() {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
//                    pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
                    pipeline.addLast("tlvDecoder", new TlvDecoder());
                    pipeline.addLast("tlvEncoder", new TlvEncoder("TlvOverTcpClient"));
                    pipeline.addLast(handler);
                }
            });

            ChannelFuture f = b.connect(address).sync();

            f.channel().closeFuture().sync();
        } catch (InterruptedException ex) {
            // do nothing
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    public void writeMessage(TlvMessage msg) {
        handler.sendMessage(msg);
    }
}
