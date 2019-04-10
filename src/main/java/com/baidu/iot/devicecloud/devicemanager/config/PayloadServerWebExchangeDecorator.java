package com.baidu.iot.devicecloud.devicemanager.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/27.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class PayloadServerWebExchangeDecorator extends ServerWebExchangeDecorator {
    private PartnerServerHttpRequestDecorator requestDecorator;
    private PartnerServerHttpResponseDecorator responseDecorator;
    protected PayloadServerWebExchangeDecorator(ServerWebExchange delegate) {
        super(delegate);
        requestDecorator = new PartnerServerHttpRequestDecorator(delegate.getRequest());
        responseDecorator = new PartnerServerHttpResponseDecorator(delegate.getResponse());
    }

    @Override
    public ServerHttpRequest getRequest() {
        return requestDecorator;
    }

    @Override
    public ServerHttpResponse getResponse() {
        return responseDecorator;
    }
}
