package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import com.bone.workflow.starter.WorkflowBoneIntegrationAutoConfiguration;
import com.bone.workflow.starter.config.MetadataSdkExtensionScanConfiguration;
import com.bone.workflow.starter.config.WorkflowSdkRepositoryConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
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
  }
}
