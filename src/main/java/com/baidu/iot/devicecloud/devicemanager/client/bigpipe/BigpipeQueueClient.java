package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import com.baidu.bigpipe.protocol.QueueMessage;

/**
 * The client for fetching message from bigpipe with queue mode.
 *
 * <p> The bigpipe have two mode for receiving side: topic and queue,
 * while the sending side do not distinguish this two modes.
 *
 * @author Shen Dayu (shendayu@baidu.com)
 **/
public interface BigpipeQueueClient {
    /**
     * Receive message from bigpipe.
     *
     * @return The receive messagae.
     * @throws Exception if any error occurred.
     */
    QueueMessage receiveMessage() throws BigpipeException;

    /**
     * Acknowledge bigpipe the message have been received.
     *
     * @param message The message to acknowledge bigpipe
     * @throws BigpipeException If any error occurred.
     */
    void acknowledgeMessage(QueueMessage message) throws BigpipeException;
}
