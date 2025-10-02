package com.bone.metadata.sdk.support.config;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MetadataServiceClientConfig {

    @ConditionalOnProperty("metadata.service.config.remote.token")
    @Bean
    public RequestInterceptor requestInterceptor(
            @Value("${metadata.service.config.remote.token:test-token}") String remoteToken,
            @Value("${metadata.service.config.remote.auth-mode:jwt}") String authMode
    ) {
        return template -> {
            if ("api-key".equalsIgnoreCase(authMode)) {
                template.header("X-API-Key", remoteToken);
            } else {
                template.header("Authorization", "Bearer " + remoteToken);
            }
        };
    }
}