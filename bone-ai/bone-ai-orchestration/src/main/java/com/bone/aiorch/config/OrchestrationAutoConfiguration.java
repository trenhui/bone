package com.bone.aiorch.config;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.domain.decision.DecisionGate;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** bone-ai-orchestration 自动配置：编排执行器（依赖规则治理面 + LLM 网关门面 + 确定性闸门）。 */
@AutoConfiguration
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
   */
  @Bean
  @ConditionalOnBean({HitlItemRepository.class, HitlDispositionRepository.class})
  @ConditionalOnMissingBean
  public HitlApplicationService hitlService(
      HitlItemRepository hitlItemRepository, HitlDispositionRepository hitlDispositionRepository) {
    return new HitlApplicationService(hitlItemRepository, hitlDispositionRepository);
  }
}
