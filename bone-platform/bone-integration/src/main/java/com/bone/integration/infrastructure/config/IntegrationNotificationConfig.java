package com.bone.integration.infrastructure.config;

import com.bone.integration.application.event.port.IntegrationEventNotifier;
import com.bone.integration.infrastructure.notification.AlertIntegrationEventNotifier;
import com.bone.integration.infrastructure.notification.CompositeIntegrationEventNotifier;
import com.bone.integration.infrastructure.notification.LoggingIntegrationEventNotifier;
import com.bone.platform.alert.AlertService;
import com.bone.platform.alert.autoconfigure.AlertAutoConfiguration;
import com.bone.platform.alert.autoconfigure.AlertProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties({IntegrationAlertProperties.class, AlertProperties.class})
public class IntegrationNotificationConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "integration.alert", name = "enabled", havingValue = "true")
    @Import(AlertAutoConfiguration.class)
    static class AlertAutoImport {}

    @Bean
    IntegrationEventNotifier integrationEventNotifier(
            IntegrationAlertProperties integrationAlertProperties,
            ObjectProvider<AlertService> alertServiceProvider) {
        List<IntegrationEventNotifier> delegates = new ArrayList<>();
        delegates.add(new LoggingIntegrationEventNotifier());
        if (integrationAlertProperties.isEnabled()) {
            alertServiceProvider.ifAvailable(
                    alertService -> delegates.add(new AlertIntegrationEventNotifier(alertService)));
        }
        return new CompositeIntegrationEventNotifier(delegates);
    }
}
