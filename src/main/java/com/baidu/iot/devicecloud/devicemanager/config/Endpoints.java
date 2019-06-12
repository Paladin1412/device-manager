package com.baidu.iot.devicecloud.devicemanager.config;

import com.baidu.iot.devicecloud.devicemanager.handler.http.AliveHandler;
import com.baidu.iot.devicecloud.devicemanager.handler.http.PushHandler;
import com.baidu.iot.devicecloud.devicemanager.handler.http.ReportHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/2/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Configuration
public class Endpoints {
    @Bean
    RouterFunction<ServerResponse> dmEndpoints(ReportHandler handler) {
        return route(i(POST("/api/v2/report")), handler::deal);
    }

    @Bean
    RouterFunction<ServerResponse> pushEndpoints(PushHandler handler) {
        return route(i(POST("/api/v2/push")), handler::deal);
    }

    @Bean
    RouterFunction<ServerResponse> aliveEndpoints(AliveHandler handler) {
        return route(GET("/alive"), handler::deal);
    }

    private static RequestPredicate i(RequestPredicate target) {
        return new CaseInsensitiveRequestPredicate(target);
    }
}
