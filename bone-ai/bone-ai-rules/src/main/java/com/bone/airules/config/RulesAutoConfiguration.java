package com.bone.airules.config;

import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.domain.repository.RuleAssetVersionRepository;
import com.bone.airules.infrastructure.engine.QlExpressRuleEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-rules 自动配置：QLExpress 引擎（{@link RuleEngine}）+ 规则治理服务。
 *
 * <p>{@link RuleEngine} 无外部依赖，常驻装配；{@link RuleApplicationService} / {@link
 * RuleQueryApplicationService} 依赖 {@link RuleAssetRepository} + {@link RuleAssetVersionRepository}
 * （端口，实现由使用方以 {@code @EnableSqlRepositories} 提供），故以 {@code @ConditionalOnBean}
 * 守卫：未接入持久化的上下文不装配治理服务，引擎仍可用。
 *
 * <p><b>为何按 Bean 名而非类型守卫（2026-10-21 补齐）</b>：SDK 仓储是 {@code RepositoryFactoryBean} 代理，接口类型只能由 {@code
 * getObjectType()} 在<b>实例化之后</b>给出（接口是构造参数，Spring 无法静态推断）——按类型判定时 {@code @ConditionalOnBean}
 * 在自动配置解析期拿不到类型，条件恒假 ⇒ 治理服务永不装配（装配级冒烟测试曾因此红）。按名判定只依赖 Bean 定义名 （SDK 注册名 = 接口名首字母小写），不依赖类型可解析性。
 */
@AutoConfiguration
@EnableConfigurationProperties(RulesProperties.class)
public class RulesAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RuleEngine ruleEngine() {
    return new QlExpressRuleEngine();
  }

  @Bean
  @ConditionalOnBean(
      name = {"ruleAssetRepository", "ruleAssetVersionRepository"} // 按名而非按类型：见类注释
      )
  @ConditionalOnMissingBean
  public RuleApplicationService ruleService(
      RuleAssetRepository repository,
      RuleAssetVersionRepository versionRepository,
      RuleEngine ruleEngine) {
    return new RuleApplicationService(repository, versionRepository, ruleEngine);
  }

  @Bean
  @ConditionalOnBean(
      name = {"ruleAssetRepository", "ruleAssetVersionRepository"} // 按名而非按类型：见类注释
      )
  @ConditionalOnMissingBean
  public RuleQueryApplicationService ruleQueryService(
      RuleAssetRepository repository, RuleAssetVersionRepository versionRepository) {
    return new RuleQueryApplicationService(repository, versionRepository);
  }
}
