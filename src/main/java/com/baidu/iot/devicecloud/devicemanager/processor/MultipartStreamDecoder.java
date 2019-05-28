package com.baidu.iot.devicecloud.devicemanager.processor;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.util.BufferUtil;
import com.fasterxml.jackson.databind.node.BinaryNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.MultipartStream;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.Part;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CONTENT_TYPE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_EQUALITY_SIGN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_SEMICOLON;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getBoundary;
import static org.apache.commons.fileupload.MultipartStream.CR;
import static org.apache.commons.fileupload.MultipartStream.DASH;
import static org.apache.commons.fileupload.MultipartStream.LF;
import static org.apache.commons.fileupload.MultipartStream.arrayequals;


/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/5/18.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class MultipartStreamDecoder {
    private static final int ONE_KB = 1024;
    /**
     * The max amount of bytes this decoder could do the work at a time
     */
    private static final int MAX_BUFFER_SIZE = ONE_KB * 4;
    private static final byte[] FIELD_SEPARATOR = {CR, LF};
    private static final byte[] STREAM_TERMINATOR = {DASH, DASH};
    private static final byte[] BOUNDARY_PREFIX = {DASH, DASH};
    private static final String EMPTY_VALUE = "";
    private static final HttpResponse defaultResponse =
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);

    private HttpResponse message;
    private HeaderParser headerParser;
    private LineParser lineParser;
    private PartParser partParser;
    /**
     * <code>bodyParser</code> should be global
     */
    @SuppressWarnings("FieldCanBeLocal")
    private BodyParser bodyParser;

    private final ByteBuf buffer;

    // These will be updated by splitHeader(...)
    private CharSequence name;
    private CharSequence value;

    private boolean connected = false;

    /**
     * The internal state of {@link MultipartStreamDecoder}.
     * <em>Internal use only</em>.
     */
    private enum State {
        SKIP_CONTROL_CHARS,
        READ_INITIAL,
        READ_HEADER,
        READ_VARIABLE_LENGTH_CONTENT,
        BAD_MESSAGE
    }

    private MultipartStreamDecoder.State currentState = MultipartStreamDecoder.State.SKIP_CONTROL_CHARS;

    @Autowired
    public MultipartStreamDecoder() {
        buffer = Unpooled.buffer(0, MAX_BUFFER_SIZE);
        AppendableCharSequence seq = new AppendableCharSequence(128);
        lineParser = new LineParser(seq, 4096);
        headerParser = new HeaderParser(seq, 8192);
    }

    Flux<Part> decode(TlvMessage message) {
        if (message != null) {
            BinaryNode valueBin = message.getValue();
            if (valueBin != null && !valueBin.isNull()) {
                @SuppressWarnings("BlockingMethodInNonBlockingContext") byte[] bytes = valueBin.binaryValue();
                return decode(bytes);
            }
        }
        return Flux.empty();
    }

    /**
     * @apiNote <code>bytes</code> could be big enough to contain multiple parts
     * @param bytes the byte array to decode
     * @return {@link Flux}&lt;{@link Part}&gt;
     */
    public Flux<Part> decode(byte[] bytes) {
        ByteBuf src = Unpooled.wrappedBuffer(bytes);
        int len = bytes.length;
        // in case of the amount of source bytes is bigger than MAX_BUFFER_SIZE
        return Flux.merge(Flux.push(sink -> {
            int b = len / MAX_BUFFER_SIZE;
            if (len % MAX_BUFFER_SIZE > 0) {
                b++;
            }
            for (int i = 0; i < b; i++) {
                byte[] toWrite = src.slice(i * MAX_BUFFER_SIZE, Math.min(MAX_BUFFER_SIZE, src.readableBytes())).array();
                if (write(toWrite) != -1) {
                    switch (currentState) {
                        case SKIP_CONTROL_CHARS: {
                            if (!skipControlCharacters(buffer)) {
                                break;
                            }
                            currentState = State.READ_INITIAL;
                        }
                        case READ_INITIAL: try {
                            AppendableCharSequence line = lineParser.parse(buffer);
                            if (line == null) {
                                break;
                            }
                            String[] initialLine = splitInitialLine(line);
                            if (initialLine.length < 3) {
                                // Invalid initial line - ignore.
                                currentState = State.SKIP_CONTROL_CHARS;
                                break;
                            }

                            message = createResponse(initialLine);
                            currentState = State.READ_HEADER;
                            // fall-through
                        } catch (Exception e) {
                            log.error("Invalid http init line", e);
                            invalidMessage(buffer);
                            sink.error(e);
                            break;
                        }
                        case READ_HEADER: {
                            State nextState = readHeaders(buffer);
                            if (nextState == null) {
                                break;
                            }
                            currentState = nextState;
                        }
                        case READ_VARIABLE_LENGTH_CONTENT: {
                            if (buffer.readableBytes() > 0) {
                                if (!connected) {
                                    String boundary = getBoundary(message.headers().get(HEADER_CONTENT_TYPE));
                                    if (StringUtils.hasText(boundary)) {
                                        bodyParser = new BodyParser(ONE_KB);
                                        partParser = new PartParser(headerParser, bodyParser, boundary.getBytes());
                                        connected = true;
                                    }
                                }
                                sink.next(partParser.parse(buffer));
                            }
                            break;
                        }
                        case BAD_MESSAGE: {
                            // Keep discarding until disconnection.
                            buffer.skipBytes(buffer.readableBytes());
                            break;
                        }
                    }
                    reset();
                }
            }
            sink.complete();
            src.release();
        }));
    }

    private void reset() {
        buffer.clear();
        buffer.capacity(ONE_KB);
    }

    private int write(byte[] bytes) {
        int len = bytes.length;
        if (buffer.writableBytes() >= len) {
            buffer.writeBytes(bytes);
            return len;
        } else {
            // Shrink the buffer and move the data to the beginning.
            int rest = buffer.readableBytes();
            if (rest > 0) {
                int total = rest + len;
                byte [] restBytes = new byte[rest];
                buffer.readBytes(restBytes)
                        .capacity(total)
                        .resetReaderIndex()
                        .resetWriterIndex()
                        .writeBytes(restBytes)
                        .writeBytes(bytes);
            } else {
                buffer
                        .capacity(len)
                        .resetReaderIndex()
                        .resetWriterIndex()
                        .writeBytes(bytes);
            }

            return len;
        }
    }

    private State readHeaders(ByteBuf buffer) {
        final HttpMessage message = this.message;
        final HttpHeaders headers = message.headers();

        AppendableCharSequence line = headerParser.parse(buffer);
        if (line == null) {
            // need more data
            return null;
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

                line = headerParser.parse(buffer);
                if (line == null) {
                    // need more data
                    return null;
                }
            } while (line.length() > 0);
        }
        // All http response headers have been read

        // Add the last header.
        if (name != null) {
            headers.add(name, value);
        }
        // reset name and value fields
        name = null;
        value = null;

        if (this.message.status() == HttpResponseStatus.OK && this.message.headers().contains(HEADER_CONTENT_TYPE)) {
            return State.READ_VARIABLE_LENGTH_CONTENT;
        }

        return State.BAD_MESSAGE;
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

    private void invalidMessage(ByteBuf in) {
        currentState = State.BAD_MESSAGE;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());
        message = null;
    }

    private static HttpResponse createResponse(String[] initialLine) {
        try {
            return new DefaultHttpResponse(
                    HttpVersion.valueOf(initialLine[0]),
                    HttpResponseStatus.valueOf(Integer.parseInt(initialLine[1]), initialLine[2]), true);
        } catch (NumberFormatException e) {
            return defaultResponse;
        }
    }

    private static boolean skipControlCharacters(ByteBuf buffer) {
        boolean skiped = false;
        final int wIdx = buffer.writerIndex();
        int rIdx = buffer.readerIndex();
        while (wIdx > rIdx) {
            int c = buffer.getUnsignedByte(rIdx++);
            if (!Character.isISOControl(c) && !Character.isWhitespace(c)) {
                rIdx--;
                skiped = true;
                break;
            }
        }
        buffer.readerIndex(rIdx);
        return skiped;
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
        public boolean process(byte value) {
            char nextByte = (char) (value & 0xFF);
            if (nextByte == HttpConstants.CR) {
                return true;
            }
            if (nextByte == HttpConstants.LF) {
                return false;
            }

            if (++ size > maxLength) {
                throw newException(maxLength);
            }

            seq.append(nextByte);
            return true;
        }

        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("HTTP header is larger than " + maxLength + " bytes.");
        }
    }

    private static final class LineParser extends HeaderParser {

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

    @Slf4j
    private static class BodyParser implements ByteProcessor {
        /**
         * <p>Save the bytes those have been processed successfully</p>
         * @apiNote {@link DataBuffer} would be extended when writing.<br>
         *     This <code>contentBuffer</code> will be as big as a single part body, <br>
         *         but of course some limitations could be apply to it.
         */
        private DataBuffer contentBuffer;

        /**
         * The initial buffer size
         */
        private final int initSize;

        private BodyParser(int initSize) {
            this.initSize = initSize;
            this.contentBuffer = BufferUtil.create(initSize);
        }

        /**
         * Read every byte in the source buffer except CR and LF
         * @param buffer the source data buffer
         * @return a new {@link DataBuffer}
         */
        private DataBuffer read(ByteBuf buffer) {
            int curReadable = contentBuffer.readableByteCount();
            int i = buffer.forEachByte(this);
            if (i == -1) {
                // waiting more body
                if (contentBuffer.readableByteCount() > curReadable) {
                    buffer.readerIndex(buffer.writerIndex());
                }
                return null;
            }
            buffer.readerIndex(i + 1);
            return readSlice();
        }

        private DataBuffer readSlice() {
            int readable = contentBuffer.readableByteCount();
            if (readable > 0) {
                DataBuffer result = BufferUtil.create(readable);
                result.write(contentBuffer.slice(contentBuffer.readPosition(), readable));
                reset();
                return result;
            }
            return BufferUtil.create(0);
        }

        /**
         * Reset <code>contentBuffer</code>'s write index and read index to 0
         */
        private void reset() {
            contentBuffer
                    .capacity(initSize)
                    .writePosition(0)
                    .readPosition(0);
        }

        @Override
        public boolean process(byte value) {
            short nextByte = (short) (value & 0xFF);
            if (nextByte == HttpConstants.CR) {
                return true;
            }
            if (nextByte == HttpConstants.LF) {
                return false;
            }

            contentBuffer.write(value);
            return true;
        }
    }

    private static class PartParser {
        private final HeaderParser headerParser;
        private final BodyParser bodyParser;
        private BufferedPart motherPart;

        /**
         * Hold the bytes those might be a part of the boundary
         */
        private DataBuffer pad;
        private int boundaryLength;
        private byte[] boundary;
        private int[] boundaryTable;

        // These will be updated by splitHeader(...)
        private CharSequence name;
        private CharSequence value;

        private PartParser(HeaderParser headerParser, BodyParser bodyParser, byte[] boundary) {
            if (boundary == null) {
                throw new IllegalArgumentException("boundary may not be null");
            }
            this.boundaryLength = boundary.length + BOUNDARY_PREFIX.length;
            this.boundary = new byte[this.boundaryLength];
            this.boundaryTable = new int[this.boundaryLength + 1];
            System.arraycopy(BOUNDARY_PREFIX, 0, this.boundary, 0,
                    BOUNDARY_PREFIX.length);
            System.arraycopy(boundary, 0, this.boundary, BOUNDARY_PREFIX.length,
                    boundary.length);
            computeBoundaryTable();

            this.headerParser = headerParser;
            this.bodyParser = bodyParser;
            this.motherPart = emptyParts.get();
            this.pad = BufferUtil.create(2);
        }

        private enum State {
            SKIP_PREAMBLE,
            READ_HEADER,
            READ_CONTENT,
            PART_FINISHED,
            STREAM_FINISHED
        }
        private State currentState = State.SKIP_PREAMBLE;

        private final Supplier<BufferedPart> emptyParts = BufferedPart::new;
        private final Function<BufferedPart, BufferedPart> copyAndReset =
                bufferedPart -> {
                    BufferedPart toSend = emptyParts.get();
                    BeanUtils.copyProperties(bufferedPart, toSend);
                    bufferedPart.setName("");
                    bufferedPart.setHttpHeaders(new org.springframework.http.HttpHeaders());
                    bufferedPart.setContents(new ArrayList<>());
                    return toSend;
                };

        /**
         * @apiNote The <code>buffer</code> could also be big enough for multiple parts
         * @param buffer the buffer to parse as {@link Part}s
         * @return {@link Flux}&lt;{@link Part}&gt;
         */
        Flux<Part> parse(ByteBuf buffer) {
            ByteBuf available;
            if (pad.readableByteCount() > 0) {
                available = makeAvailable(buffer);
            } else {
                available = buffer;
            }
            return Flux.push(partFluxSink -> {
                while (available.readableBytes() > 0) {
                    switch (currentState) {
                        case SKIP_PREAMBLE: {
                            try {
                                if (!skipPreamble(available)) {
                                    break;
                                }
                            } catch (IOException e) {
                                partFluxSink.error(e);
                                return;
                            }

                            currentState = State.READ_HEADER;
                        }
                        case READ_HEADER: {
                            State nextState;
                            try {
                                nextState = readHeaders(available);
                            } catch (IOException e) {
                                discardBodyData(available);
                                partFluxSink.error(e);
                                return;
                            }
                            if (nextState == null) {
                                break;
                            }
                            currentState = nextState;
                        }
                        case READ_CONTENT: {
                            State nextState = readBody(available);
                            if (nextState == null) {
                                break;
                            }
                            currentState = nextState;
                        }
                        case PART_FINISHED: {
                            // a part has been read successfully, publish it to process
                            partFluxSink.next(copyAndReset.apply(motherPart));
                            // parse next motherPart
                            currentState = State.SKIP_PREAMBLE;
                            break;
                        }
                        case STREAM_FINISHED: {
                            currentState = State.SKIP_PREAMBLE;
                        }
                        default: {
                            discardBodyData(available);
                            break;
                        }
                    }
                }
                partFluxSink.complete();
            });
        }

        private ByteBuf makeAvailable(ByteBuf buffer) {
            int readablePad = pad.readableByteCount();
            int size = readablePad > boundaryLength ? boundaryLength : readablePad;
            ByteBuf available = Unpooled.buffer(size + buffer.readableBytes());
            available.writeBytes(pad.asByteBuffer(pad.writePosition() - size, size))
                    .writeBytes(buffer);
            buffer.clear();
            pad.readPosition(0).writePosition(0);
            return available;
        }

        private void computeBoundaryTable() {
            int position = 2;
            int candidate = 0;

            boundaryTable[0] = -1;
            boundaryTable[1] = 0;

            while (position <= boundaryLength) {
                if (boundary[position - 1] == boundary[candidate]) {
                    boundaryTable[position] = candidate + 1;
                    candidate++;
                    position++;
                } else if (candidate > 0) {
                    candidate = boundaryTable[candidate];
                } else {
                    boundaryTable[position] = 0;
                    position++;
                }
            }
        }

        private int findSeparator(ByteBuf buffer) {
            int bufferPos = buffer.readerIndex();
            int tablePos = 0;

            while (bufferPos < buffer.writerIndex()) {
                while (tablePos >= 0 && buffer.getByte(bufferPos) != boundary[tablePos]) {
                    tablePos = boundaryTable[tablePos];
                }
                bufferPos++;
                tablePos++;
                if (tablePos == boundaryLength) {
                    return bufferPos - boundaryLength;
                }
            }
            return -1;
        }

        private boolean skipPreamble(ByteBuf buffer) throws IOException {
            int rIdx = buffer.readerIndex();
            int pos = findSeparator(buffer);

            if (pos == -1) {
                if (buffer.readableBytes() > 0) {
                    pad.write(buffer.retainedSlice().array());
                }
                // the boundary hasn't been found in the buffer
                discardBodyData(buffer);
                return false;
            }
            if (rIdx < pos) {
                buffer.skipBytes(pos - rIdx);
            }

            if (rIdx > pos) {
                buffer.readerIndex(pos);
            }
            try {
                boolean hasNext = readBoundary(buffer);
                pad.readPosition(0).writePosition(0);
                return hasNext;
            } catch (IndexOutOfBoundsException e) {
                return false;
            }
        }

        /**
         * Check the 2 bytes behind the boundary
         * @param buffer the buffer
         * @return <code>true</code> if has next part, otherwise <code>false</code>
         * @throws IOException if unexpected characters follow a boundary
         */
        private boolean readBoundary(ByteBuf buffer) throws IOException {
            byte[] marker = new byte[2];
            boolean nextChunk;

            buffer.skipBytes(boundaryLength);
            marker[0] = buffer.readByte();
            pad.write(marker[0]);
            if (marker[0] == LF) {
                // Work around IE5 Mac bug with input type=image.
                // Because the boundary delimiter, not including the trailing
                // CRLF, must not appear within any file (RFC 2046, section
                // 5.1.1), we know the missing CR is due to a buggy browser
                // rather than a file containing something similar to a
                // boundary.
                return true;
            }

            marker[1] = buffer.readByte();
            pad.write(marker[1]);
            if (arrayequals(marker, STREAM_TERMINATOR, 2)) {
                nextChunk = false;
            } else if (arrayequals(marker, FIELD_SEPARATOR, 2)) {
                nextChunk = true;
            } else {
                throw new IOException(
                        "Unexpected characters follow a boundary");
            }
            return nextChunk;
        }

        private State readHeaders(ByteBuf buffer) throws IOException {
            org.springframework.http.HttpHeaders headers = this.motherPart.getHttpHeaders();
            AppendableCharSequence line = headerParser.parse(buffer);
            if (line == null) {
                // need more data
                return null;
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
                            headers.add(name.toString(), value.toString());
                        }
                        splitHeader(line);
                    }

                    line = headerParser.parse(buffer);
                    if (line == null) {
                        // need more data
                        return null;
                    }
                } while (line.length() > 0);
            }
            // All http response headers have been read

            // Add the last header.
            if (name != null) {
                headers.add(name.toString(), value.toString());
            }
            // reset name and value fields
            name = null;
            value = null;

            String contentDisposition = headers.getFirst("Content-Disposition");
            if (StringUtils.hasText(contentDisposition)) {
                String name = getPartName(contentDisposition);
                if (StringUtils.hasText(name)) {
                    motherPart.setName(name);
//                    motherPart.setHttpHeaders(headers);
                    return State.READ_CONTENT;
                }
            }

            throw new MultipartStream.MalformedStreamException("Multipart expect the property name");
        }

        private String getPartName(String contentDisposition) {
            if (StringUtils.hasText(contentDisposition)) {
                String[] items = contentDisposition.split(Pattern.quote(SPLITTER_SEMICOLON));
                if (items.length > 1) {
                    String[] nameKV = items[1].split(Pattern.quote(SPLITTER_EQUALITY_SIGN));
                    if (nameKV.length > 1 && PARAMETER_NAME.equalsIgnoreCase(StringUtils.trimAllWhitespace(nameKV[0])) && StringUtils.hasText(nameKV[1])) {
                        return StringUtils.trimTrailingCharacter(StringUtils.trimLeadingCharacter(nameKV[1], '"'), '"');
                    }
                }
            }
            return "";
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

        private State readBody(ByteBuf buffer) {
            DataBuffer contentBuffer = bodyParser.read(buffer);
            if (contentBuffer == null) {
                // need more data
                return null;
            }
            int readable = contentBuffer.readableByteCount();
            if (readable > 0) {
                do {
                    this.motherPart.getContents().add(contentBuffer);
                    contentBuffer = bodyParser.read(buffer);
                    if (contentBuffer == null) {
                        // part is finished
                        return State.PART_FINISHED;
                    }
                } while (contentBuffer.readableByteCount() > 0);
            }
            return State.PART_FINISHED;
        }

        private void discardBodyData(ByteBuf buffer) {
            buffer.skipBytes(buffer.readableBytes());
        }
    }

    @Data
    static class BufferedPart implements Part {
        private String name;
        private org.springframework.http.HttpHeaders httpHeaders;
        private List<DataBuffer> contents;

        private BufferedPart() {
            this("", new org.springframework.http.HttpHeaders());
        }

        private BufferedPart(String name, org.springframework.http.HttpHeaders httpHeaders) {
            this(name, httpHeaders, new ArrayList<>());
        }

        private BufferedPart(String name, org.springframework.http.HttpHeaders httpHeaders, List<DataBuffer> contents) {
            this.name = name;
            this.httpHeaders = httpHeaders;
            this.contents = contents;
        }

        @NonNull
        @Override
        public String name() {
            return this.name;
        }

        @NonNull
        @Override
        public org.springframework.http.HttpHeaders headers() {
            return this.httpHeaders;
        }

        @NonNull
        @Override
        public Flux<DataBuffer> content() {
            return Flux.fromIterable(contents);
        }
    }
}
