package com.baidu.iot.devicecloud.devicemanager.config;

import org.springframework.http.server.PathContainer;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.support.ServerRequestWrapper;

import java.net.URI;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/2/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class LowerCaseUriServerRequestWrapper extends ServerRequestWrapper {
    public LowerCaseUriServerRequestWrapper(ServerRequest delegate) {
        super(delegate);
    }

    @Override
    public URI uri() {
        return URI.create(super.uri().toString().toLowerCase());
    }

    @Override
    public String path() {
        return uri().getRawPath();
    }

    @Override
    public PathContainer pathContainer() {
        return PathContainer.parsePath(path());
    }
}
