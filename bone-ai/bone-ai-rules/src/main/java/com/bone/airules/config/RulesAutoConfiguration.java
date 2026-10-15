package com.bone.airules.config;

import com.bone.airules.application.RuleQueryService;
import com.bone.airules.application.RuleService;
import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.infrastructure.engine.QlExpressRuleEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-rules 自动配置：QLExpress 引擎（{@link RuleEngine}）+ 规则治理服务。
 *
 * <p>{@link RuleEngine} 无外部依赖，常驻装配；{@link RuleService} / {@link RuleQueryService} 依赖 {@link
 * RuleAssetRepository}（端口，实现由使用方提供——批一可给内存实现、批二走 bone-metadata-sdk +
 * {@code @EnableSqlRepositories}），故以 {@code @ConditionalOnBean} 守卫：未接入持久化的上下文不装配治理服务，引擎仍可用。
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
  @ConditionalOnBean(RuleAssetRepository.class)
  @ConditionalOnMissingBean
  public RuleService ruleService(RuleAssetRepository repository, RuleEngine ruleEngine) {
    return new RuleService(repository, ruleEngine);
  }

  @Bean
  @ConditionalOnBean(RuleAssetRepository.class)
  @ConditionalOnMissingBean
  public RuleQueryService ruleQueryService(RuleAssetRepository repository) {
    return new RuleQueryService(repository);
  }
}
