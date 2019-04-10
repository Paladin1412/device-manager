package com.baidu.iot.devicecloud.devicemanager;

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
import java.util.Collections;
import java.util.UUID;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/28.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestMultipartStream {
    private String content = "HTTP/1.1 200 OK\n" +
            "Content-Type: multipart/form-data; boundary=___dueros_dcs_v1_boundary___\n" +
            "\n--___dueros_dcs_v1_boundary___\n" +
            "Content-Disposition: form-data; name=\"metadata\"\n" +
            "Content-Type: application/json; charset=utf-8\n" +
            "\n" +
            "{\"directive\":{\"header\":{\"namespace\":\"ai.dueros.device_interface.system\",\"name\":\"ThrowException\",\"messageId\":\"6be9226abae248cd845966ec46229680\"},\"payload\":{\"code\":\"INVALID_REQUEST_EXCEPTION\",\"description\":\"parser http timeout\"}}}\n\n" +
            "--___dueros_dcs_v1_boundary___--";

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
}
