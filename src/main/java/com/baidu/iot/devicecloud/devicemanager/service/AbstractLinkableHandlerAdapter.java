package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public abstract class AbstractLinkableHandlerAdapter<T extends BaseMessage> implements LinkableHandler<T> {
    private LinkableHandler<T> next;

    @Override
    public final LinkableHandler<T> linkWith(LinkableHandler<T> nextChain) {
        this.next = nextChain;
        return next;
    }

    @Override
    public final Mono<Object> handle(T it) {
        if (canHandle(it)) {
            return work(it);
        } else {
            Assert.state(next != null, "This message can't be handled");
            return this.next.handle(it);
        }
    }

    abstract boolean canHandle(T t);
    abstract Mono<Object> work(T t);
}
