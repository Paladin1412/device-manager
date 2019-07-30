package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

/**
 * The interface for bigpipe client provider.
 *
 * @author Shen Dayu (shendayu@baidu.com)
 **/
public interface BigpipeClientProvider {
    /**
     * Get a queue mode bigpipe client for receiving side.
     *
     * @param queue The queue name.
     * @return The bigpipe queue client.
     * @throws BigpipeException If any error occurred.
     */
    BigpipeQueueClient getBigpipeQueueClient(String queue) throws BigpipeException;

    /**
     * Get a bigpipe client for sending side.
     *
     * @param queue The queue name.
     * @return The bigpipe writer client.
     * @throws BigpipeException If any error occurred.
     */
    BigpipeWriterClient getBigpipeWriterClient(String queue) throws BigpipeException;
}
