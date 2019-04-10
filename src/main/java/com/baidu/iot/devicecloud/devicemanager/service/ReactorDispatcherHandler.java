package com.baidu.iot.devicecloud.devicemanager.service;

import reactor.core.publisher.Mono;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public interface ReactorDispatcherHandler<T> {
    Mono<Object> handle(T t);
}
