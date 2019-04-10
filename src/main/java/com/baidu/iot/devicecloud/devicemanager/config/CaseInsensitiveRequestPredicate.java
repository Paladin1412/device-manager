package com.baidu.iot.devicecloud.devicemanager.config;

import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/2/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class CaseInsensitiveRequestPredicate implements RequestPredicate {
    private final RequestPredicate target;

    CaseInsensitiveRequestPredicate(RequestPredicate target) {
        this.target = target;
    }

    @Override
    public boolean test(ServerRequest request) {
        return this.target.test(new LowerCaseUriServerRequestWrapper(request));
    }

    @Override
    public String toString() {
        return this.target.toString();
    }
}
