package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import com.baidu.bigpipe.impl.QueueBlockedClient;
import com.baidu.bigpipe.protocol.QueueMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * The implementation of {@link BigpipeQueueClient}
 *
 * @author Zheng Xiaoxiong(zhengxiaoxiong@baidu.com)
 */
@Slf4j
public class BigpipeQueueClientImpl implements BigpipeQueueClient {

    private final String queueName;

    private final QueueBlockedClient client;

    private final BigpipeConfig config;

    private boolean isInitialized = false;

    public BigpipeQueueClientImpl(BigpipeConfig config, String queueName) {
        this.config = config;
        this.queueName = queueName;
        client = new QueueBlockedClient(this.config.getClusterName());
    }


    @Override
    public QueueMessage receiveMessage() throws BigpipeException {
        try {
            if (!isInitialized) {
                initialQueue();
            }
            return client.fetchMessage();
        } catch (Exception e) {
            log.warn("receiveMessage failed from queue {}, try again.", queueName);
            try {
                openQueue();
            } catch (Exception e2) {
                closeQuietly();
                throw e2;
            }
            throw new BigpipeException(true, "ignore. receiveMessage failed from queue " + queueName, e);
        }
    }

    @Override
    public void acknowledgeMessage(final QueueMessage message) throws BigpipeException {
        try {
            if (!isInitialized) {
                initialQueue();
            }
            client.acknowledgeMessage(message);
        } catch (Exception e) {
            closeQuietly();
            throw new BigpipeException("acknowledgeMessage failed from queue " + queueName, e);
        }
    }

//
//    @Override
//    public void acknowledgeMessage(final QueueMessage message) throws BigpipeException {
//        callWrapper(this, new Caller<Void>() {
//            @Override
//            public Void call() throws QueueInteractException {
//                client.acknowledgeMessage(message);
//                return null;
//            }
//
//            @Override
//            public String getCallerName() {
//                return "acknowledgeMessage";
//            }
//        });
//    }

    private void closeQuietly() {
        try {
            client.close();
        } catch (Exception e) {
            log.error("Close writer failed. queueName={} exception={}", queueName, e);
        }
        isInitialized = false;
    }

    private void openQueue() throws BigpipeException {
        if (isInitialized) {
            closeQuietly();
        }
        try {
            client.open();
//            client.getSocket().setSoTimeout(Integer.parseInt(config.getQueueTimeout()));
//            client.getSocket().setTcpNoDelay(true);

            client.sendRequest();
            isInitialized = true;
        } catch (Exception e) {
//            log.error("Cannot open queue. queue={}", queueName, e);
            throw new BigpipeException("Open queue failed. queueName=" + queueName, e);
        }
    }

    private void initialQueue() throws BigpipeException {
        client.setQueueName(queueName);
        client.setToken(config.getQueueToken());
        client.setWindowSize(Integer.parseInt(config.getQueueWindowSize()));
//        if (!openQueue()) {
//            throw new BigpipeException("Cannot initial queue. queueName=" + queueName);
//        }
        openQueue();
    }


//    private static <T> T callWrapper(BigpipeQueueClientImpl client, Caller<T> caller) throws BigpipeException {
//        // We do not wanna use 'synchronized' to make this client thread-safe.
//        // Add 'synchronized' just in case.
////        synchronized (client) {
//        try {
//            if (!client.opened) {
//                client.initialQueue();
//            }
//            return caller.call();
//        } catch (QueueInteractException e) {
//            boolean ignorable = false;
//            // The SocketTimeoutException is known and can't solve, so ignore it when logging.
//            if (SocketTimeoutException.class == e.getCause().getClass()) {
//                // The log info is too much to log.
//                // log.debug("Error of interacting with queue is socket timeout, ignore it. queueName={}",
//                //        client.queueName);
//                ignorable = true;
//            } else if (IOException.class == e.getCause().getClass()) {
//                log.warn("Error of interacting with queue is IOException, ignore it. callerName={}, queueName={}",
//                        caller.getCallerName(), client.queueName);
//                client.closeQuietly();
//            } else {
//                log.error("Interact with queue failed. callerName={}, queueName={}",
//                        caller.getCallerName(), client.queueName, e);
//                client.closeQuietly();
//            }
//            throw new BigpipeException(ignorable, "Interact failed from queue " + client.queueName, e);
//        }
////        }
//    }


//    public QueueMessage receiveMessage() throws BigpipeException {
//        return callWrapper(this, new Caller<QueueMessage>() {
//            @Override
//            public QueueMessage call() throws QueueInteractException {
//                return client.fetchMessage();
//            }
//
//            @Override
//            public String getCallerName() {
//                return "receiveMessage";
//            }
//        });
//    }


//    @Deprecated
//    private interface Caller<T> {
//        T call() throws QueueInteractException;
//
//        String getCallerName();
//    }
}
