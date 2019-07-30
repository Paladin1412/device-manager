package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

/**
 * The interface for bigpipe writer client.
 *
 * @author Shen Dayu (shendayu@baidu.com)
 **/
public interface BigpipeWriterClient {
    /**
     * Send message to bigpipe.
     *
     * @param message The message to send.
     * @throws BigpipeException if any error occurred.
     */
    void sendMessage(String message) throws BigpipeException;

    String getPipeletName();
}
