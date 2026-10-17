package com.bone.aiintegration.config;

import com.bone.aiintegration.application.IntegrationApplicationService;
import com.bone.aiintegration.domain.gateway.ExternalSystemPort;
import com.bone.aiintegration.domain.gateway.IntegrationAuditPort;
import com.bone.aiintegration.infrastructure.integration.HttpExternalSystemAdapter;
import com.bone.aiintegration.infrastructure.integration.NoOpIntegrationAuditAdapter;
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
  public IntegrationAuditPort integrationAuditPort() {
    return new NoOpIntegrationAuditAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public IntegrationApplicationService integrationService(
      ExternalSystemPort externalSystemPort, IntegrationAuditPort auditPort) {
    return new IntegrationApplicationService(externalSystemPort, auditPort);
  }
}
