package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.service.LinkableHandler;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public abstract class AbstractLinkableDataPointHandler implements LinkableHandler<DataPointMessage> {
    private LinkableHandler<DataPointMessage> next;

    @Override
    public final LinkableHandler<DataPointMessage> linkWith(LinkableHandler<DataPointMessage> nextChain) {
        this.next = nextChain;
        return next;
    }

    @Override
    public final Mono<Object> handle(DataPointMessage it) {
        if (canHandle(getDataPointType(it))) {
            return work(it);
        } else {
            Assert.state(next != null, "This message can't be handled");
            return this.next.handle(it);
        }
    }

    final String getDataPointType(DataPointMessage it) {
        if (it == null || StringUtils.isEmpty(it.getPath())) {
            return null;
        }
        return PathUtil.dropOffPrefix(it.getPath(), SPLITTER_URL);
    }

    abstract boolean canHandle(String type);
    abstract Mono<Object> work(DataPointMessage message);
}
