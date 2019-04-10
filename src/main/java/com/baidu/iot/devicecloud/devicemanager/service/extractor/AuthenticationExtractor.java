package com.baidu.iot.devicecloud.devicemanager.service.extractor;

import com.baidu.iot.devicecloud.devicemanager.bean.AuthorizationMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PreconditionUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/22.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class AuthenticationExtractor extends AbstractLinkableExtractor {
    @Override
    boolean canHandle(int type) {
        return type == MessageType.AUTHORIZATION;
    }

    @Override
    Mono<Object> work(ServerRequest request) {
        AuthorizationMessage parsed = new AuthorizationMessage();
        assembleFromHeader(request, parsed);
        return request.body(BodyExtractors.toMono(String.class))
                .log()
                .flatMap(requestBodyString -> {
                    JsonNode requestBody = JsonUtil.readTree(requestBodyString.getBytes());
                    PreconditionUtil.checkInput(requestBody.hasNonNull(CommonConstant.PARAMETER_TOKEN), "Token is null");
                    PreconditionUtil.checkInput(
                            requestBody.hasNonNull(CommonConstant.PARAMETER_UUID)
                                    || requestBody.hasNonNull(CommonConstant.PARAMETER_CUID),
                            String.format("Both %s and %s are null", CommonConstant.PARAMETER_UUID, CommonConstant.PARAMETER_CUID)
                    );

                    parsed.setToken(requestBody.get(CommonConstant.PARAMETER_TOKEN).asText());
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_UUID)).ifPresent(
                            uuid -> parsed.setUuid(uuid.asText())
                    );
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_CUID)).ifPresent(
                            cuid -> parsed.setCuid(cuid.asText())
                    );
                    return Mono.just(parsed);
                });
    }
}
