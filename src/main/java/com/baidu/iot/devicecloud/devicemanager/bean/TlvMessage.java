package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.databind.node.BinaryNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

import static com.google.common.base.Charsets.UTF_8;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Data
public class TlvMessage {
    // 16-bit unsigned short
    private int type;

    // 32-bit unsigned int
    private long length;

    private BinaryNode value;

    public TlvMessage(int type, long length) {
        this(type, length, BinaryNode.valueOf(new byte[0]));
    }

    public TlvMessage(int type, long length, byte[] content) {
        this(type, length, fromBytes0(length, content));
    }

    public TlvMessage(int type, long length, BinaryNode value) {
        this.type = type;
        this.length = length;
        this.value = value;
    }

    private static BinaryNode fromBytes0(long length, byte[] content) {
        if (null == content) {
            throw new NullPointerException("No content.");
        }

        if (content.length != length) {
            throw new IllegalArgumentException("Content's length illegal.");
        }
        return BinaryNode.valueOf(content);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() +
                "(" +
                String.format("type=%d,", type) +
                String.format(" length=%d,", length) +
                String.format(" value=%s", value == null ? "null" : UTF_8.decode(ByteBuffer.wrap(value.binaryValue()))) +
                ")";
    }
}
