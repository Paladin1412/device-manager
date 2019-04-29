package com.baidu.iot.devicecloud.devicemanager.service.extractor;

import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.service.LinkableHandler;
import com.baidu.iot.devicecloud.devicemanager.service.ReactorDispatcherHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Component
public class ReportMessageExtractor implements ReactorDispatcherHandler<ServerRequest> {
    private LinkableHandler<ServerRequest> extractor;

    @Autowired
    ReportMessageExtractor(DataPointExtractor dataPointExtractor,
                           AuthenticationExtractor authenticationExtractor,
                           DefaultExtractor defaultExtractor) {
        // The first link in chain is supposed to handle the most requests
        this.extractor = dataPointExtractor;
        // Link the chain up
        this.extractor
                .linkWith(authenticationExtractor)
                .linkWith(defaultExtractor);
    }

    @Override
    public Mono<Object> handle(ServerRequest serverRequest) {
        return Mono.from(checkHeaders(serverRequest))
                .switchIfEmpty(Mono.defer(() ->Mono.from(this.extractor.handle(serverRequest))));
    }

    private Mono<Object> checkHeaders(ServerRequest request) {
        ServerRequest.Headers httpHeaders = request.headers();
        if (withoutHeader.apply(httpHeaders, CommonConstant.HEADER_CLT_ID)) {
            return Mono.error(
                    new ServerWebInputException(
                            String.format("Missing request header '%s'",
                                    CommonConstant.HEADER_CLT_ID)
                    )
            );
        }

        if (withoutHeader.apply(httpHeaders, CommonConstant.HEADER_SN)) {
            return Mono.error(
                    new ServerWebInputException(
                            String.format("Missing request header '%s'",
                                    CommonConstant.HEADER_SN)
                    )
            );
        }

        if (withoutHeader.apply(httpHeaders, CommonConstant.HEADER_MESSAGE_TYPE)) {
            return Mono.error(
                    new ServerWebInputException(
                            String.format("Missing request header '%s'",
                                    CommonConstant.HEADER_MESSAGE_TYPE)
                    )
            );
        }
        return Mono.empty();
    }

    private final BiFunction<ServerRequest.Headers, String, Boolean> withoutHeader = (h, k) -> h.header(k).size() < 1;
}
