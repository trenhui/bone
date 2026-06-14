package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.domain.exception.ExceptionHandler;
import com.bone.metadata.sdk.extension.ColumnAllocator;
import com.bone.metadata.sdk.extension.repository.FieldMetadataRepository;
import com.bone.metadata.sdk.metadata.DelegatingMetadataService;
import com.bone.metadata.sdk.metadata.EmbeddedMetadataService;
import com.bone.metadata.sdk.metadata.MetadataHealthIndicator;
import com.bone.metadata.sdk.metadata.RemoteMetadataService;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.metadata.client.MetadataServiceClient;
import com.bone.metadata.sdk.support.util.DistributedLockUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(MetadataSdkProperties.class)
@Slf4j
public class MetadataAutoConfiguration {

  private final MetadataSdkProperties props;
  private final DataSourceProperties dsProps;

  public MetadataAutoConfiguration(MetadataSdkProperties props, DataSourceProperties dsProps) {
    this.props = props;
    this.dsProps = dsProps;
  }

  @Bean
  @ConditionalOnMissingBean
  @Primary
  public MetadataSdkContext metadataSdkContext() {
    MetadataSdkContext context = new MetadataSdkContext(props, dsProps);
    log.info(
        "MetadataSdkContext initialized - appCode: {}, deploymentMode: {}, databaseType: {}",
        context.getAppCode(),
        context.getDeploymentMode(),
        context.getDatabaseType());
    return context;
  }

  @Bean
  @ConditionalOnEnabledHealthIndicator("metadataService")
  public MetadataHealthIndicator metadataHealthIndicator(
      DelegatingMetadataService delegatingService) {
    return new MetadataHealthIndicator(delegatingService);
  }

  @Bean
  @Primary
  public DelegatingMetadataService delegatingMetadataService(
      ApplicationContext applicationContext, MetadataSdkProperties properties) {
    return new DelegatingMetadataService(applicationContext, properties);
  }

  @ConditionalOnProperty(
      name = "metadata.sdk.deploymentMode",
      havingValue = "EMBEDDED",
      matchIfMissing = true // ⭐️ 如果没配置也满足条件
      )
  @Bean
  public MetadataService embeddedMetadataService(
      ColumnAllocator allocator, FieldMetadataRepository repository) {
    return new EmbeddedMetadataService(allocator, repository);
  }

  @Bean
  @ConditionalOnProperty(name = "metadata.sdk.deploymentMode", havingValue = "REMOTE")
  public MetadataService remoteMetadataService(MetadataServiceClient client) {
    return new RemoteMetadataService(client);
  }

  @Bean
  public DistributedLockUtil distributedLockUtil() {
    return new DistributedLockUtil();
  }

  // 新增：注册 ExceptionHandler 为 Spring Bean
  @Bean
  @ConditionalOnMissingBean
  public ExceptionHandler exceptionHandler() {
    return ExceptionHandler.getInstance();
  }
}
