package com.bone.integration.infrastructure.config;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelConfig {
    @Bean
    public CamelContextConfiguration camelContextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                // 配置Camel上下文
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // 应用启动后的配置
            }
        };
    }

    @Bean
    public RouteBuilder routeBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // 配置默认路由
                from("direct:start")
                        .log("Starting integration flow")
                        .to("direct:end");
            }
        };
    }
}