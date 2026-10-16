package com.bone.aiorch.config;

import com.bone.ai.gateway.application.LlmGatewayService;
import com.bone.aiorch.application.HitlService;
import com.bone.aiorch.application.OrchestrationService;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.airules.application.RuleQueryService;
import com.bone.airules.application.RuleService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** bone-ai-orchestration 自动配置：编排执行器（依赖规则治理面 + LLM 网关门面）。 */
@AutoConfiguration
public class OrchestrationAutoConfiguration {

  @Bean
  @ConditionalOnBean({RuleService.class, RuleQueryService.class, LlmGatewayService.class})
  @ConditionalOnMissingBean
  public OrchestrationService orchestrationService(
      RuleService ruleService,
      RuleQueryService ruleQueryService,
      LlmGatewayService llmGatewayService) {
    return new OrchestrationService(ruleService, ruleQueryService, llmGatewayService);
  }

  /** HITL 人工终审子域（§4.2；依赖 SDK 生成的 HitlItemRepository，未启用持久化时缺 Bean 自动跳过）。 */
  @Bean
  @ConditionalOnBean(HitlItemRepository.class)
  @ConditionalOnMissingBean
  public HitlService hitlService(HitlItemRepository hitlItemRepository) {
    return new HitlService(hitlItemRepository);
  }
}
