package com.baidu.iot.devicecloud.devicemanager.util;

import com.google.common.base.Charsets;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.slf4j.Logger;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/27.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class LogUtils {
    public static final List<MediaType> legalLogMediaTypes = Lists.newArrayList(
            MediaType.TEXT_XML,
            MediaType.APPLICATION_XML,
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_JSON_UTF8,
            MediaType.TEXT_PLAIN,
            MediaType.MULTIPART_FORM_DATA,
            MediaType.TEXT_XML);

    public static <T extends DataBuffer> void loggingRequest(Logger log, T buffer) {
        logging(log, ">>>>>>>>>>", buffer);
    }

    public static <T extends DataBuffer> T loggingResponse(Logger log, T buffer) {
        return logging(log, "<<<<<<<<<<", buffer);
    }

    private static <T extends DataBuffer> T logging(Logger log, String inOrOut, T buffer) {
        try {
            InputStream dataBuffer = buffer.asInputStream();
            byte[] bytes = ByteStreams.toByteArray(dataBuffer);
            NettyDataBufferFactory nettyDataBufferFactory = new NettyDataBufferFactory(new UnpooledByteBufAllocator(false));
            if (log.isDebugEnabled()) {
//                log.debug("\n" +
//                        "{}Payload    : \n{}", inOrOut, ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(bytes)));
                log.debug("\n" +
                        "{}Payload    : \n{}", inOrOut, new String(bytes, Charsets.UTF_8));
            }
            DataBufferUtils.release(buffer);
            return (T) nettyDataBufferFactory.wrap(bytes);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static Function<Logger, RemovalListener<Object, Object>> REMOVAL_LOGGER =
            logger -> n -> logger.debug("Removed ({}, {}), caused by: {}", n.getKey(), n.getValue(), n.getCause().toString());
}
