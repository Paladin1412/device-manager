package com.baidu.iot.devicecloud.devicemanager.service;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public interface LinkableHandler<T> extends ReactorDispatcherHandler<T> {
    LinkableHandler<T> linkWith(LinkableHandler<T> nextChain);
}
