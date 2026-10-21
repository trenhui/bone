package com.bone.aiorch.config;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.config.LlmGatewayAutoConfiguration;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.domain.decision.DecisionGate;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.config.RulesAutoConfiguration;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-orchestration 自动配置：编排执行器（依赖规则治理面 + LLM 网关门面 + 确定性闸门）。
 *
 * <p><b>跨自动配置顺序（2026-10-21 补齐）</b>：编排执行器的 {@code @ConditionalOnBean} 依赖规则治理服务与网关门面两个 Bean， 而
 * {@code @ConditionalOnBean} 只能看见<b>已处理过</b>的 Bean 定义——自动配置按类名排序时 {@code com.bone.ai*} 早于 {@code
 * com.bone.metadata.sdk.*}，且本类与规则 / 网关之间无序可依，条件曾恒假 ⇒ 编排服务永不装配（装配级冒烟测试曾因此红）。
 * {@code @AutoConfigureAfter} 显式声明「本类在规则 / 网关自动配置之后处理」，条件才看得到它们的 Bean 定义。
 */
@AutoConfiguration
@AutoConfigureAfter({RulesAutoConfiguration.class, LlmGatewayAutoConfiguration.class})
public class OrchestrationAutoConfiguration {

  /**
   * 确定性闸门（阈值可配；默认置信度 0.85 / 金额 5000）。
   *
   * <p>阈值走配置而非硬编码：不同险种 / 客群的小额自动线不同（车险 5000 与寿险 5 万显然不该同一阈值）。
   */
  @Bean
  @ConditionalOnMissingBean
  public DecisionGate decisionGate(
      @Value("${bone.ai.orchestration.confidence-threshold:0.85}") double confidenceThreshold,
      @Value("${bone.ai.orchestration.amount-threshold:5000}") String amountThreshold) {
    return new DecisionGate(confidenceThreshold, new BigDecimal(amountThreshold));
  }

  @Bean
  @ConditionalOnBean({
    RuleApplicationService.class,
    RuleQueryApplicationService.class,
    LlmGatewayApplicationService.class
  })
  @ConditionalOnMissingBean
  public OrchestrationApplicationService orchestrationService(
      RuleApplicationService ruleService,
      RuleQueryApplicationService ruleQueryService,
      LlmGatewayApplicationService llmGatewayService,
      DecisionGate decisionGate) {
    return new OrchestrationApplicationService(
        ruleService, ruleQueryService, llmGatewayService, decisionGate);
  }

  /**
   * HITL 人工终审子域（§4.2；依赖 SDK 生成的 HitlItemRepository + HitlDispositionRepository，未启用持久化时缺 Bean 自动跳过）。
   *
   * <p>按 Bean 名守卫：SDK 仓储是 FactoryBean 代理，接口类型在实例化前不可静态推断（按类型判定条件恒假）。
   */
  @Bean
  @ConditionalOnBean(name = {"hitlItemRepository", "hitlDispositionRepository"})
  @ConditionalOnMissingBean
  public HitlApplicationService hitlService(
      HitlItemRepository hitlItemRepository, HitlDispositionRepository hitlDispositionRepository) {
    return new HitlApplicationService(hitlItemRepository, hitlDispositionRepository);
  }
}
