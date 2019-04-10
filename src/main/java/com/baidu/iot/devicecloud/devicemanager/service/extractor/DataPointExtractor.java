package com.baidu.iot.devicecloud.devicemanager.service.extractor;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DEFAULT_VERSION;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/22.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DataPointExtractor extends AbstractLinkableExtractor {
    @Override
    boolean canHandle(int type) {
        return type == MessageType.DATA_POINT;
    }

    @Override
    Mono<Object> work(ServerRequest request) {
        DataPointMessage parsed = new DataPointMessage();
        assembleFromHeader(request, parsed);

        return request.body(BodyExtractors.toMono(JsonNode.class))
                .flatMap(requestBody -> {
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_ID)).ifPresent(
                            id -> parsed.setId(id.asInt(IdGenerator.nextId()))
                    );
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_VERSION)).ifPresent(
                             version -> parsed.setVersion(version.asInt(DEFAULT_VERSION))
                    );
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_CODE)).ifPresent(
                            code -> parsed.setCode(code.asInt())
                    );
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_PATH)).ifPresent(
                            path -> parsed.setPath(PathUtil.lookAfterPrefix(path.asText()))
                    );
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_PAYLOAD)).ifPresent(
                            payload -> parsed.setPayload(payload.asText())
                    );
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_QUERY)).ifPresent(
                            query -> parsed.setQuery(query.asText())
                    );
                    Optional.ofNullable(requestBody.get(CommonConstant.PARAMETER_MISC)).ifPresent(
                            misc -> parsed.setMisc(misc.asText())
                    );
                    return Mono.just(parsed);
                });
    }
}
