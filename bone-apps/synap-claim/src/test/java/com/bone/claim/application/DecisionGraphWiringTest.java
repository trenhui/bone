package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.handler.AdjustNodeHandler;
import com.bone.claim.application.handler.AiAssessNodeHandler;
import com.bone.claim.application.handler.ArbitrateNodeHandler;
import com.bone.claim.application.handler.EvidenceLoadNodeHandler;
import com.bone.claim.application.handler.RuleCheckNodeHandler;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.infrastructure.ai.LocalAiGatewayPortAdapter;
import com.bone.claim.infrastructure.context.LocalClaimContextPortAdapter;
import com.bone.claim.infrastructure.forensics.LocalVisualForensicsPortAdapter;
import com.bone.claim.infrastructure.fraud.LocalGraphFraudPortAdapter;
import com.bone.claim.infrastructure.knowledge.LocalKnowledgeRetrievalPortAdapter;
import com.bone.claim.infrastructure.notify.LocalNotificationPortAdapter;
import com.bone.claim.infrastructure.rule.LocalRuleEnginePortAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 装配冒烟测试：证明 5 个决策节点处理器经 Spring 组件扫描被自动收集进 {@link DecisionGraphExecutor}。
 *
 * <p>历史问题：{@code DecisionNodeHandler} 端口零实现，运行期 {@code Map} 为空 → 决策图每个节点 {@code handler==null} →
 * 确定性节点 SKIPPED → 最终 {@code CAPABILITY_MISSING → MANUAL_REQUIRED}（永远转人工的假绿）。 本测试用 {@link
 * ApplicationContextRunner}（沿用仓库 bone-app-purchase / bone-app-expense 既定范式，避免脆弱的 全上下文
 * {@code @SpringBootTest}）断言执行器真正收齐 5 个节点。
 */
class DecisionGraphWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(WiringConfig.class);

  @Test
  void collectsAllFiveDecisionNodeHandlersIntoExecutor() {
    runner.run(
        ctx -> {
          // 5 个处理器各自注册为 bean
          assertThat(ctx).hasSingleBean(EvidenceLoadNodeHandler.class);
          assertThat(ctx).hasSingleBean(RuleCheckNodeHandler.class);
          assertThat(ctx).hasSingleBean(AiAssessNodeHandler.class);
          assertThat(ctx).hasSingleBean(AdjustNodeHandler.class);
          assertThat(ctx).hasSingleBean(ArbitrateNodeHandler.class);

          // 7 个智能化出站端口均有本地确定性桩实现（防腐层落地，二期接 bone-ai）
          assertThat(ctx).hasSingleBean(LocalClaimContextPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalRuleEnginePortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalAiGatewayPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalKnowledgeRetrievalPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalVisualForensicsPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalGraphFraudPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalNotificationPortAdapter.class);

          // 关键断言：执行器真正收集到 5 个节点（此前为 0 → 假绿）
          DecisionGraphExecutor executor = ctx.getBean(DecisionGraphExecutor.class);
          assertThat(executor.handlers())
              .containsOnlyKeys("EvidenceLoad", "RuleCheck", "AiAssess", "Adjust", "Arbitrate");
        });
  }

  @Configuration
  @ComponentScan(
      basePackages = {
        "com.bone.claim.application.handler",
        "com.bone.claim.infrastructure.context",
        "com.bone.claim.infrastructure.rule",
        "com.bone.claim.infrastructure.ai",
        "com.bone.claim.infrastructure.knowledge",
        "com.bone.claim.infrastructure.forensics",
        "com.bone.claim.infrastructure.fraud",
        "com.bone.claim.infrastructure.notify"
      })
  @Import(DecisionGraphExecutor.class)
  static class WiringConfig {
    @Bean
    ClaimCaseRepository claimCaseRepository() {
      return Mockito.mock(ClaimCaseRepository.class);
    }

    @Bean
    EvidenceRepository evidenceRepository() {
      return Mockito.mock(EvidenceRepository.class);
    }

    @Bean
    DecisionExecutionApplicationService decisionExecutionApplicationService() {
      return Mockito.mock(DecisionExecutionApplicationService.class);
    }

    @Bean
    ClaimCaseApplicationService claimCaseApplicationService() {
      return Mockito.mock(ClaimCaseApplicationService.class);
    }

    @Bean
    TenantPort tenantPort() {
      return Mockito.mock(TenantPort.class);
    }
  }
}
