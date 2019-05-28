package com.baidu.iot.devicecloud.devicemanager.util;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.PamConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/6.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class TlvUtil {
    private static List<Integer> legalTypes;

    static {
        legalTypes = Arrays.asList(
                TlvConstant.TYPE_UPSTREAM_INIT,
                TlvConstant.TYPE_UPSTREAM_ASR,
                TlvConstant.TYPE_UPSTREAM_DUMI,
                TlvConstant.TYPE_UPSTREAM_FINISH,
                TlvConstant.TYPE_DOWNSTREAM_INIT,
                TlvConstant.TYPE_DOWNSTREAM_ASR,
                TlvConstant.TYPE_DOWNSTREAM_DUMI,
                TlvConstant.TYPE_DOWNSTREAM_TTS,
                TlvConstant.TYPE_DOWNSTREAM_FINISH,
                TlvConstant.TYPE_DOWNSTREAM_PRE_TTS
        );
    }

    public static boolean isLegalType(int type) {
        return legalTypes.contains(type);
    }

    public static boolean isLegalLength(long length) {
        return length > 0;
    }

    public static boolean confirmedConnection(TlvMessage msg) {
        int type = msg.getType();
        BinaryNode valueBin = msg.getValue();

        if (type == TlvConstant.TYPE_DOWNSTREAM_INIT
                && valueBin != null
                && !valueBin.isNull()) {
            try {
                ObjectNode valueNode = (ObjectNode) JsonUtil.readTree(valueBin.binaryValue());
                int status = valueNode.path(PamConstant.PAM_PARAM_STATUS).asInt(-1);

                return status == TlvConstant.CONNECTION_OK;
            } catch (Exception e) {
                log.error("Checking if the connection could be confirmed failed", e);
            }
        }
        return false;
    }

    private static BiFunction<TlvMessage, Integer, Boolean> checkType =
            (tlv, type) -> {
                try {
                    return tlv.getType() == type;
                } catch (Exception e) {
                    log.error("Checking the tlv message's type failed", e);
                    return false;
                }
            };

    public static Predicate<TlvMessage> isAsrTlv = tlv -> checkType.apply(tlv, TlvConstant.TYPE_DOWNSTREAM_ASR);
    public static Predicate<TlvMessage> isDirectiveTlv = tlv -> checkType.apply(tlv, TlvConstant.TYPE_DOWNSTREAM_DUMI);
    public static Predicate<TlvMessage> isTTSTlv = tlv -> checkType.apply(tlv, TlvConstant.TYPE_DOWNSTREAM_TTS);
    public static Predicate<TlvMessage> isPreTTSTlv = tlv -> checkType.apply(tlv, TlvConstant.TYPE_DOWNSTREAM_PRE_TTS);
    public static Predicate<TlvMessage> isDownStreamFinishTlv = tlv -> checkType.apply(tlv, TlvConstant.TYPE_DOWNSTREAM_FINISH);

    public static boolean isUpstreamInitPackage(TlvMessage msg) {
        return checkType(msg, TlvConstant.TYPE_UPSTREAM_INIT);
    }

    public static boolean isUpstreamFinishPackage(TlvMessage msg) {
        return checkType(msg, TlvConstant.TYPE_UPSTREAM_FINISH);
    }

    public static boolean isDownstreamInitPackage(TlvMessage msg) {
        return checkType(msg, TlvConstant.TYPE_DOWNSTREAM_INIT);
    }

    public static boolean isDownstreamFinishPackage(TlvMessage msg) {
        return checkType(msg, TlvConstant.TYPE_DOWNSTREAM_FINISH);
    }

    public static boolean isAsrPackage(TlvMessage msg) {
        return isAsrTlv.test(msg);
    }

    private static boolean checkType(TlvMessage msg, int type) {
        try {
            return msg.getType() == type;
        } catch (Exception e) {
            log.error("Checking the tlv message's type failed", e);
            return false;
        }
    }

    public static List<TlvMessage> extractMessagesFrom(ByteBuf in) {
        List<TlvMessage> results = new LinkedList<>();
        while (true) {
            if (notEnoughData(in)) {
                break;
            }

            in.markReaderIndex();

            int type = in.readUnsignedShortLE();

            if (!isLegalType(type)) {
                in.resetReaderIndex();
                break;
            }

            long len = in.readUnsignedIntLE();

            if (!isLegalLength(len)) {
                in.resetReaderIndex();
                break;
            }

            if (in.readableBytes() < len) {
                in.resetReaderIndex();
                break;
            } else {
                byte[] data;
                try {
                    data = new byte[Math.toIntExact(len)];
                } catch (ArithmeticException e) {
                    break;
                }
                in.readBytes(data);

                results.add(new TlvMessage(type, len, data));
            }
        }
        return results;
    }

    private static boolean notEnoughData(ByteBuf in) {
        return in.readableBytes() < 6;
    }

    public static TlvMessage demo(int type, String s) {
        if (StringUtils.isEmpty(s)) {
            s = "";
        }
        return new TlvMessage(type, s.length(), s.getBytes());
    }

    public static Function<TlvMessage, String> prettyLogString =
            tlv -> {
                String logString;
                try {
                    logString = ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(tlv.getValue().binaryValue()));
                } catch (Exception ignore) {
                    logString = String.valueOf(tlv);
                    log.warn("Trying to get the pretty hex dump failed, using String.valueOf().");
                }
                return logString;
            };
}
