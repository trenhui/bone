package com.bone.integration.infrastructure.camel;

import com.bone.integration.infrastructure.camel.http.CustomHeaderFilterStrategy;
import com.bone.integration.infrastructure.camel.http.ExtendHttpComponent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 平台集成 Camel 上下文（自 legacy engine 抽取 HTTP 组件，供 INT-11 路由编译使用）。
 */
@Component
@ConditionalOnProperty(prefix = "integration.camel", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@Getter
public class CamelIntegrationContext {

    private final CamelContext camelContext = new DefaultCamelContext();

    @PostConstruct
    public void start() throws Exception {
        camelContext.setManagementName("bone-integration-camel");
        ExtendHttpComponent extendHttpComponent = new ExtendHttpComponent();
        camelContext.addComponent("extend-http", extendHttpComponent);
        camelContext.addComponent("extend-https", extendHttpComponent);
        extendHttpComponent.setHeaderFilterStrategy(new CustomHeaderFilterStrategy());
        camelContext.start();
        log.info("Camel integration context started");
    }

    @PreDestroy
    public void stop() throws Exception {
        if (camelContext.isStarted()) {
            camelContext.stop();
        }
    }
}
