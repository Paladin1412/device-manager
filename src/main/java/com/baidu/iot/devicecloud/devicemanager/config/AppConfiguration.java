package com.baidu.iot.devicecloud.devicemanager.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;
import org.springframework.web.server.WebFilter;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Configuration
public class AppConfiguration {
    @Bean
    public Jackson2ObjectMapperFactoryBean objectMapperFactory() {
        Jackson2ObjectMapperFactoryBean factoryBean = new Jackson2ObjectMapperFactoryBean();
        factoryBean.setFeaturesToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        factoryBean.setFeaturesToEnable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        factoryBean.setAnnotationIntrospector(new JacksonAnnotationIntrospector());
        return factoryBean;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter webFilter() {
        return (exchange, chain) -> chain.filter(new PayloadServerWebExchangeDecorator(exchange));
    }
}
