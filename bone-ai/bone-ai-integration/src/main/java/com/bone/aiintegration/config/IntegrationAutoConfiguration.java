package com.bone.aiintegration.config;

import com.bone.aiintegration.application.IntegrationService;
import com.bone.aiintegration.domain.gateway.ExternalSystemPort;
import com.bone.aiintegration.infrastructure.integration.HttpExternalSystemAdapter;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** bone-ai-integration 自动配置（Boot 3 {@code .imports} 机制）。 */
@AutoConfiguration
public class IntegrationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ExternalSystemPort externalSystemPort() {
    return new HttpExternalSystemAdapter(HttpClient.newHttpClient());
  }

  @Bean
  @ConditionalOnMissingBean
  public IntegrationService integrationService(ExternalSystemPort externalSystemPort) {
    return new IntegrationService(externalSystemPort);
  }
}
