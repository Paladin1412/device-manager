package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import com.baidu.bigpipe.impl.BigpipeBlockedWriter;
import com.baidu.bigpipe.protocol.BigpipePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

/**
 * The implementation of {@link BigpipeWriterClient}.
 *
 * @author Zheng Xiaoxiong (zhengxiaoxiong@baidu.com)
 */
public class BigpipeWriterClientImpl implements BigpipeWriterClient {

    private static final int RESEND_TIMES = 3;

    private static int writerIndex = 0;

    private final BigpipeBlockedWriter writer;

    private final BigpipeConfig config;

    private final String pipletname;

    private volatile boolean initialized = false;

    private Logger log;

    /**
     * Sending time interval.
     */
    private int sendInterval = 100;

    public BigpipeWriterClientImpl(BigpipeConfig config, String pipletname) throws BigpipeException {
        this.log = LoggerFactory.getLogger(pipletname + "-" + writerIndex++);
        this.config = config;
        this.pipletname = pipletname;
        writer = new BigpipeBlockedWriter(config.getClusterName());
        writer.setUsername(config.getUsername());
        writer.setPassword(config.getPassword());
        writer.setPipeletName(pipletname);
//        initialBigpipeWriter(pipletname);
    }

    @Override
    public void sendMessage(String message) throws BigpipeException {

        if (!initialized) {
            initialBigpipeWriter(pipletname);
        }

        // We do not wanna use 'synchronized' to make this writer thread-safe.
        // Add 'synchronized' just in case.
//        synchronized (writer) {
        for (int sendNum = 1; sendNum <= RESEND_TIMES; sendNum++) {
            try {
                log.debug("Bigpipe send message pipeletname={}, id={}", writer.getPipeletName(), writer.getId());
                long startTime = System.currentTimeMillis();
                BigpipePacket packet = writer.doSendOne(message);
                long endTime = System.currentTimeMillis();
                log.info("Bigpipe write succeeded. topicMessageId={}, message={}, sendTime={}",
                        packet.command.getAck().getTopicMessageId(), message, (endTime - startTime));
                long remainTime = sendInterval - (endTime - startTime);
                if (remainTime > 0) {
                    try {
                        Thread.sleep(remainTime);
                    } catch (InterruptedException e) {
                        log.error("Send message sleep exception", e);
                    }
                }
                return;
            } catch (Exception e) {
                sendMsgExceptionHandle(sendNum, message, e);
                // closeQuietly();
            }
        }
    }

    @Override
    public String getPipeletName() {
        return pipletname;
    }

    private void sendMsgExceptionHandle(int sendNum, String message, Exception e) throws BigpipeException {
        log.error("Send message failed: times={}, pipletname={}, message={} error={}", sendNum, pipletname, message, e.getMessage());
        log.info("Bigpipe reconnect, pipletname={}", pipletname);

        if (connectBigpipeWriter(true)) {
            log.info("Bigpipe reconnect succeeded, pipletname={}", pipletname);
        } else {
            log.error("Bigpipe reconnect failed, pipletname={}", pipletname);
        }
        if (sendNum >= RESEND_TIMES) {
            throw new BigpipeException("Bigpipe send message failed.", e);
        }
    }

    private void closeQuietly() {
        try {
            writer.close();
            initialized = false;
        } catch (IOException e) {
            log.error("Close writer failed. pipletname={}", pipletname, e);
        }
    }

    private synchronized boolean connectBigpipeWriter(boolean reconnect) throws BigpipeException {
        if (reconnect) {
            closeQuietly();
        }
        try {
            writer.setId(UUID.randomUUID().toString());
            writer.open();
            BigpipePacket packet = writer.doConnect();
            log.info("Bigpipe connect, pipletname={}, packet={}", writer.getPipeletName(), packet.toString());
            return true;
        } catch (Exception e) {
            log.error("Bigpipe connect bigpipe writer failed. pipletname={}, exception={}", pipletname, e);
            closeQuietly();
            return false;
        }
    }

    private void initialBigpipeWriter(String pipletname) throws BigpipeException {
        if (!connectBigpipeWriter(false)) {
            throw new BigpipeException("Bigpipe cannot open initial writer, pipeletname=" + pipletname);
        }
        initialized = true;
    }

}