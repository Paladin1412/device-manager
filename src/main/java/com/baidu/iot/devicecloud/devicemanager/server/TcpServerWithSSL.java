package com.baidu.iot.devicecloud.devicemanager.server;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import io.netty.buffer.ByteBuf;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.util.function.Function;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/5.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class TcpServerWithSSL {
    public void init() throws CertificateException, SSLException {
        SelfSignedCertificate cert = new SelfSignedCertificate();
        SslContextBuilder serverOptions = SslContextBuilder.forServer(cert.certificate(), cert.privateKey());
        SslContext clientOptions = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        final TcpServer server =
                TcpServer.create()
                        .host("localhost")
                        .secure(sslContextSpec -> sslContextSpec.sslContext(serverOptions));

        DisposableServer connectedServer = server.handle((in, out) -> {
            in.withConnection(c -> c.onTerminate().doOnNext((n) -> System.out.println("terminated." + n.toString())))
            .receive()
                    .map(TLVExtractor)
                    .log("conn")
                    .subscribe(tlv -> {
                        log.debug(tlv.toString());
                    });

            return out.sendString(Mono.just("Hi"))
                    .neverComplete();
        })
                .wiretap(true)
                .bindNow();
    }

    final static Function<ByteBuf, TlvMessage> TLVExtractor = bb -> {
        if (bb.readableBytes() > 6) {
            short type = bb.readShort();
            int length = bb.readInt();

            if (length <= 0) {
                return new TlvMessage(type, length);
            }

            return new TlvMessage(type, length, bb.readBytes(length).array());

        }
        return null;
    };
}
