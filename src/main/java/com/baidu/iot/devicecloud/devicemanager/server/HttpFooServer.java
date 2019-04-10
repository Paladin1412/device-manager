package com.baidu.iot.devicecloud.devicemanager.server;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class HttpFooServer {

    public static void main(String[] args) {
        String test = "{\"name\": \"userException\",  \"data\": [{\"clt_id\":  \"-1001_3F1806118800C678\", \"timestamp\": 15000}]}";
        DisposableServer server = HttpServer.create()
                .port(8999)
//                .tcpConfiguration(tcpServer -> tcpServer.option(ChannelOption.SO_KEEPALIVE, true))
                .route(routes ->
                    routes.post("/test", ((request, response) -> {
                        request.receive().asString()
                                .map(s -> {System.out.println(s);return s;});
                        return response.sendString(Mono.just(test)).neverComplete();
                    }
                    ))
                )
                .wiretap(true)
                .bindNow();

        HttpClient.create()
                .port(server.port())
                .post()
                .uri("/test")
                .send(ByteBufFlux.fromString(Flux.just("Hello")))
                .responseContent()
                .aggregate()
                .log("http-client")
                .block();
    }
}
