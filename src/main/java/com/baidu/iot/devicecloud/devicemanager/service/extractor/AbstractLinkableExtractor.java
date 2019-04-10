package com.baidu.iot.devicecloud.devicemanager.service.extractor;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.service.LinkableHandler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.assembleFromHeader;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getFirst;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public abstract class AbstractLinkableExtractor implements LinkableHandler<ServerRequest> {
    private LinkableHandler<ServerRequest> next;

    @Override
    public final LinkableHandler<ServerRequest> linkWith(LinkableHandler<ServerRequest> nextChain) {
        this.next = nextChain;
        return next;
    }

    @Override
    public final Mono<Object> handle(ServerRequest it) {
        if (canHandle(getMessageType(it))) {
            return work(it);
        } else {
            Assert.state(next != null, "This message can't be handled");
            return this.next.handle(it);
        }
    }

    private int getMessageType(ServerRequest it) {
        String headerType = getFirst(it, CommonConstant.HEADER_MESSAGE_TYPE);
        try {
            if (StringUtils.hasText(headerType)) {
                return Integer.parseInt(headerType);
            }
        } catch (NumberFormatException ignore) {}
        return -1;
    }

    abstract boolean canHandle(int type);
    abstract Mono<Object> work(ServerRequest t);

    final void assembleFromHeader(ServerRequest request, BaseMessage message) {
        assembleFromHeader.accept(request, message);
    }
}
