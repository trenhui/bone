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
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
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

  /**
   * Long 一律序列化为 JSON 字符串（Spring MVC ObjectMapper）。
   *
   * <p>雪花 ID 是 18～19 位 long，超出 JS Number 安全上限（2^53），以 JSON number 返回时前端拿到的 ID
   * 末几位被静默截断（758267976611790848 → ...800），回传后端即 404 且无任何 JS 报错。所有应用都 {@code @Import}
   * 本配置，这里是全平台统一兜底的唯一落点；既有约定 {@code AbstractDTO.id} 的逐字段 ToStringSerializer
   * 由此推广为全局。反序列化不受影响（Jackson 自动把字符串入参转回 Long）。
   */
  @Bean
  @ConditionalOnMissingBean(name = "boneLongToStringCustomizer")
  public Jackson2ObjectMapperBuilderCustomizer boneLongToStringCustomizer() {
    return builder ->
        builder
            .serializerByType(Long.class, ToStringSerializer.instance)
            .serializerByType(Long.TYPE, ToStringSerializer.instance);
  }

  // ⚠️ @ConditionalOnBean：extension-studio 自带 @Primary 的 studioMetadataService 占位实现，
  // 此时本自动配置里的 delegatingMetadataService 已被下方的
  // @ConditionalOnMissingBean(name="studioMetadataService") 抑制；若仍强行创建本指示器，
  // 会因缺少 DelegatingMetadataService 而启动失败。
  @Bean
  @ConditionalOnEnabledHealthIndicator("metadataService")
  @ConditionalOnBean(DelegatingMetadataService.class)
  public MetadataHealthIndicator metadataHealthIndicator(
      DelegatingMetadataService delegatingService) {
    return new MetadataHealthIndicator(delegatingService);
  }

  // ⚠️ 必须 @ConditionalOnMissingBean(name="studioMetadataService")：extension-studio 在
  // StudioMetadataBootstrapConfiguration 中提供了同名 @Primary 占位实现（控制面不需要 SDK 真实元数据能力），
  // 若不做此让步，studio 上下文里会出现 2 个 @Primary 的 MetadataService，Spring 启动直接抛
  // NoUniqueBeanDefinitionException。其他模块不定义 studioMetadataService，本 Bean 照常创建。
  @Bean
  @Primary
  @ConditionalOnMissingBean(name = "studioMetadataService")
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

  // ⚠️ 必须加 @ConditionalOnMissingBean：extension-studio 走 EMBEDDED 模式时会自带同名
  // 的 DistributedLockUtil Bean，而自动配置类总是最后求值，宿主已注册 ⇒ SDK 这一份必须让位。
  // 早期本方法漏了该条件，直接导致 extension-studio 启动报「bean 'distributedLockUtil' 无法注册」
  // （同类里 metadataSdkContext / embeddedMetadataService / exceptionHandler 都带了，唯独这里漏了）。
  @Bean
  @ConditionalOnMissingBean
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
