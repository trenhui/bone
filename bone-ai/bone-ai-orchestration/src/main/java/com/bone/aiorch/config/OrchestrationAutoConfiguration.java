package com.bone.aiorch.config;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** bone-ai-orchestration 自动配置：编排执行器（依赖规则治理面 + LLM 网关门面）。 */
@AutoConfiguration
public class OrchestrationAutoConfiguration {

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
      LlmGatewayApplicationService llmGatewayService) {
    return new OrchestrationApplicationService(ruleService, ruleQueryService, llmGatewayService);
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
