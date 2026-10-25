package com.bone.workflow.starter.sdk;

import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.runtime.application.RuntimeActionApplicationService;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import com.bone.workflow.starter.WorkflowBoneIntegrationAutoConfiguration;
import com.bone.workflow.starter.config.MetadataSdkExtensionScanConfiguration;
import com.bone.workflow.starter.config.WorkflowSdkRepositoryConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.sql.DataSource;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * {@code wf_*} SDK 仓储的共享 H2 测试上下文。
 *
 * <p><b>与宿主生产的差异</b>：宿主（bone-metadata-server）的 {@code MetadataService} 走真实元数据栈；测试上下文 里同一 bean 链由 H2
 * + SDK 自动配置复现（{@code EmbeddedMetadataService} 构造期不触库，故无需捆绑 ext schema）。 生产 SQL
 * 本身不经过元数据栈——{@code @Sql} 静态文本 + 命名参数直连 JDBC。
 *
 * <p><b>为什么导入生产桥接装配</b>（{@link WorkflowBoneIntegrationAutoConfiguration}）：适配器的正确性一半在 「bean 链起得来」（五个
 * SDK 基建 bean + 扩展仓储扫描 + 仓储代理注册）。从上下文按<b>端口类型</b>取用生产 {@code @Bean} 方法创建的适配器，装配缺陷（缺 bean / 条件恒假 /
 * 注册遗漏）会在此现形，而不是到宿主启动才爆炸。
 *
 * <p><b>宿主服务替身</b>（{@link #jdbcRuntimeRecordService} / {@link
 * #runtimeActionApplicationService}）：桥接装配的 {@code workflowRecordPort} / {@code workflowStatusPort}
 * / {@code workflowStatusIntentExecutor} 自 2026-10-25 起<b>硬装配</b>（端口缺失由注入点
 * fail-fast，不再静默跳过引擎），其依赖的宿主服务在本上下文不存在， 故以替身满足：各 H2 用例的执行器/状态端口均由用例自建桩（见 {@code
 * StatusProjectionRelayH2Test} / {@code WorkflowEngineWiringH2Test}）， 这两个 bean
 * 只参与「上下文起得来」这一装配断言，不被任何用例调用。
 */
public final class WorkflowSdkTestContext {

  private WorkflowSdkTestContext() {}

  public static AnnotationConfigApplicationContext create() {
    return new AnnotationConfigApplicationContext(WorkflowSdkTestConfig.class);
  }

  /**
   * H2 + SDK 自动配置 + 生产桥接装配的测试配置。
   *
   * <p>基建口径（spike 实证）：{@code SqlRepositoryAutoConfiguration} 只需 jdbc + properties； {@code
   * MetadataAutoConfiguration} 提供 DelegatingMetadataService → EmbeddedMetadataService →
   * ColumnAllocator + FieldMetadataRepository 链；{@link MetadataSdkExtensionScanConfiguration} 扫
   * {@code com.bone.metadata.sdk.extension}（{@code RepositoryFactoryBean} 代理的前置）。
   */
  @Configuration
  @ImportAutoConfiguration({SqlRepositoryAutoConfiguration.class, MetadataAutoConfiguration.class})
  @Import({
    MetadataSdkExtensionScanConfiguration.class,
    WorkflowSdkRepositoryConfig.class,
    WorkflowBoneIntegrationAutoConfiguration.class
  })
  static class WorkflowSdkTestConfig {

    @Bean
    EmbeddedDatabase embeddedDatabase() {
      return new EmbeddedDatabaseBuilder()
          .setType(EmbeddedDatabaseType.H2)
          .addScript("classpath:wf-schema-h2.sql")
          .build();
    }

    @Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    DataSourceProperties dataSourceProperties() {
      return new DataSourceProperties();
    }

    /**
     * 宿主运行时记录服务替身：实体目录返空（本上下文无已发布元数据，各用例也不经它读写）。
     *
     * <p>与宿主 {@code RuntimeEngineConfiguration} 的 6 参构造不同，此处用 2 参构造（审计 sink / 入口状态解析 / 关系支持 /
     * 写钩子全部走 NOOP 默认值）——端口 bean 只需「能构造」。
     */
    @Bean
    JdbcRuntimeRecordService jdbcRuntimeRecordService(
        NamedParameterJdbcTemplate jdbcTemplate, RuntimeEntityCatalog runtimeEntityCatalog) {
      return new JdbcRuntimeRecordService(jdbcTemplate, runtimeEntityCatalog);
    }

    @Bean
    RuntimeEntityCatalog runtimeEntityCatalog() {
      return (entityCode, tenantId) -> Optional.empty();
    }

    /**
     * 宿主动作应用服务替身：Mockito mock（无任何用例调用生产 {@code StatusIntentExecutor}，见类注释）。
     *
     * <p>不手工构造它的 8 个依赖（动作定义/执行仓储、分发器、访问守卫、UI Schema 执行器、角色提供者）——那些依赖 与 {@code wf_*} 适配器的装配断言无关，mock
     * 保持测试上下文对宿主内部图的最小耦合。
     */
    @Bean
    RuntimeActionApplicationService runtimeActionApplicationService() {
      return Mockito.mock(RuntimeActionApplicationService.class);
    }
  }
}
