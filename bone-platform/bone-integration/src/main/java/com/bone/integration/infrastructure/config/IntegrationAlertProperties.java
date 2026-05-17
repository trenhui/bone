package com.bone.integration.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "integration.alert")
public class IntegrationAlertProperties {

    /** 是否启用 bone-notification 告警通道（仍始终写结构化日志） */
    private boolean enabled = false;
}
