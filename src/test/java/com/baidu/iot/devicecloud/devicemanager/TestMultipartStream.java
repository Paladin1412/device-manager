package com.baidu.iot.devicecloud.devicemanager;

import com.baidu.iot.devicecloud.devicemanager.processor.MultipartStreamDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import org.apache.commons.fileupload.MultipartStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/28.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestMultipartStream {
    private static final byte LF = 0x0A;
    private static final byte CR = 0x0D;
    private static final byte DASH = 0x2D;

    private static final byte[] HEADER_SEPARATOR = {CR, LF, CR, LF};
    private static final byte[] FIELD_SEPARATOR = {CR, LF};
    private static final byte[] STREAM_TERMINATOR = {DASH, DASH};
    private static final byte[] BOUNDARY_PREFIX = {CR, LF, DASH, DASH};
    private String content = "HTTP/1.1 200 OK\n" +
            "Content-Type: multipart/form-data; boundary=___dueros_dcs_v1_boundary___\n" +
            "\n--___dueros_dcs_v1_boundary___\n" +
            "Content-Disposition: form-data; name=\"metadata\"\n" +
            "Content-Type: application/json; charset=utf-8\n" +
            "\n" +
            "{\"directive\":{\"header\":{\"namespace\":\"ai.dueros.device_interface.system\",\"name\":\"ThrowException\",\"messageId\":\"6be9226abae248cd845966ec46229680\"},\"payload\":{\"code\":\"INVALID_REQUEST_EXCEPTION\",\"description\":\"parser http timeout\"}}}\n\n" +
            "--___dueros_dcs_v1_boundary___--";

    private byte[] boundary;
    private byte[] buffer;
    private int[] boundaryTable;
    private int boundaryLength;

    private MultipartStreamDecoder decoder = new MultipartStreamDecoder();

    @Test
    public void test() {
        InputStream in = new ByteArrayInputStream(content.getBytes());
        MultipartStream multipartStream = new MultipartStream(in, "___dueros_dcs_v1_boundary___".getBytes(), 1 << 10, null);

        try {
            boolean hasNextPart = multipartStream.skipPreamble();

            while (hasNextPart) {
                String headers = multipartStream.readHeaders();
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                multipartStream.readBodyData(data);
                hasNextPart = multipartStream.readBoundary();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMultipartBody() {
        RequestBody multipartBody = new MultipartBody.Builder()
                .setType(MediaType.get(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE))
                .addPart(MultipartBody.Part.createFormData("metadata", "123123123"))
                .build();

        System.out.println(multipartBody);
        Buffer buffer = new Buffer();
        try {
            multipartBody.writeTo(buffer);
            System.out.println(buffer.readUtf8());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testFindSeparator() {
        String boundaryString = "sample";
        byte[] bb = boundaryString.getBytes();
        String header = "Content-Disposition: form-data; name=\"metadata\"";
        byte[] headerBytes = header.getBytes();
        String content = "{\"directive\":{\"header\":{\"namespace\":\"ai.dueros.device_interface.voice_output\",\"name\":\"Speak\",\"dialogRequestId\":\"string\",\"messageId\":\"NWM4MjA4NTMyMjQyYzQ0NTk=\"},\"payload\":{\"token\":\"eyJib3RfaWQiOiJ1cyIsInJlc3VsdF90b2tlbiI6ImIwODY3ZDQzOGEzYmNiMGU2YTg4ZTc4OTEwOWMzMGI5IiwiYm90X3Rva2VuIjoibnVsbCIsImxhdW5jaF9pZHMiOlsiIl19\",\"format\":\"AUDIO_MPEG\",\"url\":\"cid:34\"}}}";
        byte[] contentBytes = content.getBytes();
        this.boundaryLength = bb.length + BOUNDARY_PREFIX.length;
        this.boundary = new byte[this.boundaryLength];
        this.boundaryTable = new int[this.boundaryLength + 1];
        System.arraycopy(BOUNDARY_PREFIX, 0, boundary, 0, BOUNDARY_PREFIX.length);
        System.arraycopy(bb, 0, boundary, BOUNDARY_PREFIX.length, bb.length);
        computeBoundaryTable();
        int bufSize = 2
                + bb.length
                + 2
                + headerBytes.length
                + HEADER_SEPARATOR.length
                + contentBytes.length
                + 2
                + boundaryLength
                + 2;
        this.buffer = new byte[bufSize];
        System.arraycopy(STREAM_TERMINATOR, 0, buffer, 0, STREAM_TERMINATOR.length);
        System.arraycopy(bb, 0, buffer, STREAM_TERMINATOR.length, bb.length);
        System.arraycopy(FIELD_SEPARATOR, 0, buffer, STREAM_TERMINATOR.length + bb.length, 2);
        System.arraycopy(headerBytes, 0, buffer, STREAM_TERMINATOR.length + bb.length + 2, headerBytes.length);
        System.arraycopy(HEADER_SEPARATOR, 0, buffer, STREAM_TERMINATOR.length + bb.length + 2 + headerBytes.length, 4);
        System.arraycopy(contentBytes, 0, buffer, STREAM_TERMINATOR.length + bb.length + 2 + headerBytes.length + 4, contentBytes.length);
        System.arraycopy(FIELD_SEPARATOR, 0, buffer, STREAM_TERMINATOR.length + bb.length + 2 + headerBytes.length + 4 + contentBytes.length, 2);
        System.arraycopy(boundary, 0, buffer, STREAM_TERMINATOR.length + bb.length + 2 + headerBytes.length + 4 + contentBytes.length + 2, boundaryLength);
        System.arraycopy(STREAM_TERMINATOR, 0, buffer, STREAM_TERMINATOR.length + bb.length + 2 + headerBytes.length + 4 + contentBytes.length + 2 + boundaryLength, 2);
        System.out.println(findSeparator());
        String initLine = "Http/1.1 200 OK";
        byte[] initLineBytes = initLine.getBytes();
        ByteBuf bf0 = Unpooled.buffer(initLineBytes.length + 2);
        bf0.writeBytes(initLineBytes);
        bf0.writeBytes(FIELD_SEPARATOR);
        decoder.decode(bf0.retainedSlice().array())
                .subscribe(System.out::println);
        String header1 = "Content-Type: multipart/form-data; boundary=sample";
        byte[] header1Bytes = header1.getBytes();
        ByteBuf bf1 = Unpooled.buffer(header1Bytes.length + 2);
        bf1.writeBytes(header1Bytes);
        bf1.writeBytes(FIELD_SEPARATOR);
        decoder.decode(bf1.retainedSlice().array())
                .log("decoded1")
                .subscribe();

        decoder.decode(new byte[]{CR})
                .log("decoded2")
                .subscribe();

        decoder.decode(new byte[]{LF})
                .log("decoded3")
                .subscribe();

        decoder.decode(buffer)
                .log("decoded4")
                .subscribe();

        try {
            Thread.sleep(50000);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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

    private int findSeparator() {

        int bufferPos = 0;
        int tablePos = 0;

        while (bufferPos < this.buffer.length) {
            while (tablePos >= 0 && buffer[bufferPos] != boundary[tablePos]) {
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
}
