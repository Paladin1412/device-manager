package com.baidu.iot.devicecloud.devicemanager.constant;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.parseIntTo2BytesInOrder;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TlvConstant {
    public static final int TYPE_UPSTREAM_INIT = 0X0001;
    public static final int TYPE_UPSTREAM_ASR = 0X0002;
    public static final int TYPE_UPSTREAM_DUMI = 0X0003;
    public static final int TYPE_UPSTREAM_FINISH = 0X0004;

    public static final int TYPE_DOWNSTREAM_INIT = 0XF001;
    public static final int TYPE_DOWNSTREAM_ASR = 0XF002;
    public static final int TYPE_DOWNSTREAM_DUMI = 0XF003;
    public static final int TYPE_DOWNSTREAM_TTS = 0XF004;
    public static final int TYPE_DOWNSTREAM_FINISH = 0XF005;
    public static final int TYPE_DOWNSTREAM_PRE_TTS = 0XF006;

    public static final int CONNECTION_OK = 0;
    public static final ByteBuf CONNECTION_CONFIRMED = confirm(ByteOrder.LITTLE_ENDIAN);
    public static final TlvMessage CONNECTION_CONFIRMED_TLV = confirm();

    private static ByteBuf confirm(ByteOrder byteOrder) {
        ByteBuf confirmed = Unpooled.buffer().alloc().heapBuffer();
        confirmed.writeBytes(ByteBuffer.allocate(2).order(byteOrder).put(parseIntTo2BytesInOrder(TYPE_DOWNSTREAM_INIT, byteOrder)).compact());

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode status = mapper.createObjectNode();
        status.set(PamConstant.PAM_PARAM_STATUS, IntNode.valueOf(CONNECTION_OK));
        final ObjectWriter writer = mapper.writer();
        byte[] bytes;
        try {
            bytes = writer.writeValueAsBytes(status);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            bytes = "{\"status\":0}".getBytes();
        }
        int length = bytes.length;
        confirmed.writeBytes(ByteBuffer.allocate(4).order(byteOrder).putInt(length).compact());
        confirmed.writeBytes(ByteBuffer.allocate(length).order(byteOrder).put(bytes).compact());
        return confirmed;
    }

    private static TlvMessage confirm() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode status = mapper.createObjectNode();
        status.set(PamConstant.PAM_PARAM_STATUS, IntNode.valueOf(CONNECTION_OK));
        final ObjectWriter writer = mapper.writer();
        byte[] bytes;
        try {
            bytes = writer.writeValueAsBytes(status);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            bytes = "{\"status\":0}".getBytes();
        }
        int length = bytes.length;
        return new TlvMessage(TYPE_DOWNSTREAM_INIT, length, bytes);
    }
}
