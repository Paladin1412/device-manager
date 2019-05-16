package com.baidu.iot.devicecloud.devicemanager.processor;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.AppendableCharSequence;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/29.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class MultipartFormDataProcessor {
    private static final String EMPTY_VALUE = "";

    private HttpResponse message;
    private HeaderParser headerParser;
    private LineParser lineParser;
    private final ByteBuf multipart;
    private final HttpResponse defaultResponse;

    // These will be updated by splitHeader(...)
    private CharSequence name;
    private CharSequence value;

    public MultipartFormDataProcessor(ByteBuf multipart) {
        if (multipart == null || multipart.readableBytes() < 1) {
            throw new IllegalStateException("ByteBuf is null");
        }
        this.multipart = multipart;
        AppendableCharSequence seq = new AppendableCharSequence(128);
        lineParser = new MultipartFormDataProcessor.LineParser(seq, 4096);
        headerParser = new MultipartFormDataProcessor.HeaderParser(seq, 8192);
        defaultResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);

        init();
    }

    private void init() {
        String[] initLine = readInit();
        if (initLine == null) {
            message = defaultResponse;
        } else {
            message = createResponse(initLine);
        }
        readHeaders();
    }

    private String[] readInit() {
        AppendableCharSequence line = lineParser.parse(multipart);
        if (line == null) {
            return null;
        }
        String[] initialLine = splitInitialLine(line);
        if (initialLine.length < 3) {
            // Invalid initial line - ignore.
            return null;
        }
        return initialLine;
    }

    private void readHeaders() {
        final HttpMessage message = this.message;
        final HttpHeaders headers = message.headers();

        AppendableCharSequence line = headerParser.parse(multipart);
        if (line == null) {
            return;
        }
        if (line.length() > 0) {
            do {
                char firstChar = line.charAt(0);
                if (name != null && (firstChar == ' ' || firstChar == '\t')) {
                    //please do not make one line from below code
                    //as it breaks +XX:OptimizeStringConcat optimization
                    String trimmedLine = line.toString().trim();
                    String valueStr = String.valueOf(value);
                    value = valueStr + ' ' + trimmedLine;
                } else {
                    if (name != null) {
                        headers.add(name, value);
                    }
                    splitHeader(line);
                }

                line = headerParser.parse(multipart);
                if (line == null) {
                    return;
                }
            } while (line.length() > 0);
        }

        // Add the last header.
        if (name != null) {
            headers.add(name, value);
        }
        // reset name and value fields
        name = null;
        value = null;
    }

    private void splitHeader(AppendableCharSequence sb) {
        final int length = sb.length();
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;

        nameStart = findNonWhitespace(sb, 0);
        for (nameEnd = nameStart; nameEnd < length; nameEnd ++) {
            char ch = sb.charAt(nameEnd);
            if (ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
        }

        for (colonEnd = nameEnd; colonEnd < length; colonEnd ++) {
            if (sb.charAt(colonEnd) == ':') {
                colonEnd ++;
                break;
            }
        }

        name = sb.subStringUnsafe(nameStart, nameEnd);
        valueStart = findNonWhitespace(sb, colonEnd);
        if (valueStart == length) {
            value = EMPTY_VALUE;
        } else {
            valueEnd = findEndOfString(sb);
            value = sb.subStringUnsafe(valueStart, valueEnd);
        }
    }

    public ByteBuf slice() {
        int rIdx = multipart.readerIndex();
        int wIdx = multipart.writerIndex();
        log.debug("rIdx={}, wIdx={}", rIdx, wIdx);
        return multipart.retainedSlice();
    }

    public HttpResponse response() {
        return this.message;
    }

    private static HttpResponse createResponse(String[] initialLine) {
        return new DefaultHttpResponse(
                HttpVersion.valueOf(initialLine[0]),
                HttpResponseStatus.valueOf(Integer.parseInt(initialLine[1]), initialLine[2]), true);
    }

    private static String[] splitInitialLine(AppendableCharSequence sb) {
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        int cStart;
        int cEnd;

        aStart = findNonWhitespace(sb, 0);
        aEnd = findWhitespace(sb, aStart);

        bStart = findNonWhitespace(sb, aEnd);
        bEnd = findWhitespace(sb, bStart);

        cStart = findNonWhitespace(sb, bEnd);
        cEnd = findEndOfString(sb);

        return new String[] {
                sb.subStringUnsafe(aStart, aEnd),
                sb.subStringUnsafe(bStart, bEnd),
                cStart < cEnd? sb.subStringUnsafe(cStart, cEnd) : "" };
    }

    private static int findNonWhitespace(AppendableCharSequence sb, int offset) {
        for (int result = offset; result < sb.length(); ++result) {
            if (!Character.isWhitespace(sb.charAtUnsafe(result))) {
                return result;
            }
        }
        return sb.length();
    }

    private static int findWhitespace(AppendableCharSequence sb, int offset) {
        for (int result = offset; result < sb.length(); ++result) {
            if (Character.isWhitespace(sb.charAtUnsafe(result))) {
                return result;
            }
        }
        return sb.length();
    }

    private static int findEndOfString(AppendableCharSequence sb) {
        for (int result = sb.length() - 1; result > 0; --result) {
            if (!Character.isWhitespace(sb.charAtUnsafe(result))) {
                return result + 1;
            }
        }
        return 0;
    }

    private static class HeaderParser implements ByteProcessor {
        private final AppendableCharSequence seq;
        private final int maxLength;
        private int size;

        HeaderParser(AppendableCharSequence seq, int maxLength) {
            this.seq = seq;
            this.maxLength = maxLength;
        }

        public AppendableCharSequence parse(ByteBuf buffer) {
            final int oldSize = size;
            seq.reset();
            int i = buffer.forEachByte(this);
            if (i == -1) {
                size = oldSize;
                return null;
            }
            buffer.readerIndex(i + 1);
            return seq;
        }

        void reset() {
            size = 0;
        }

        @Override
        public boolean process(byte value) throws Exception {
            char nextByte = (char) (value & 0xFF);
            if (nextByte == HttpConstants.CR) {
                return true;
            }
            if (nextByte == HttpConstants.LF) {
                return false;
            }

            if (++ size > maxLength) {
                // TODO: Respond with Bad Request and discard the traffic
                //    or close the connection.
                //       No need to notify the upstream handlers - just log.
                //       If decoding a response, just throw an exception.
                throw newException(maxLength);
            }

            seq.append(nextByte);
            return true;
        }

        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("HTTP header is larger than " + maxLength + " bytes.");
        }
    }

    private static final class LineParser extends MultipartFormDataProcessor.HeaderParser {

        LineParser(AppendableCharSequence seq, int maxLength) {
            super(seq, maxLength);
        }

        @Override
        public AppendableCharSequence parse(ByteBuf buffer) {
            reset();
            return super.parse(buffer);
        }

        @Override
        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("An HTTP line is larger than " + maxLength + " bytes.");
        }
    }
}
