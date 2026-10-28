package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.infrastructure.MockLlmClient;
import com.bone.claim.application.handler.AdjustNodeHandler;
import com.bone.claim.application.handler.AiAssessNodeHandler;
import com.bone.claim.application.handler.ArbitrateNodeHandler;
import com.bone.claim.application.handler.EvidenceLoadNodeHandler;
import com.bone.claim.application.handler.RuleCheckNodeHandler;
import com.bone.claim.application.port.out.DecisionMetricsPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.infrastructure.client.AiGatewayPortAdapter;
import com.bone.claim.infrastructure.context.LocalClaimContextPortAdapter;
import com.bone.claim.infrastructure.forensics.LocalVisualForensicsPortAdapter;
import com.bone.claim.infrastructure.fraud.LocalGraphFraudPortAdapter;
import com.bone.claim.infrastructure.knowledge.LocalKnowledgeRetrievalPortAdapter;
import com.bone.claim.infrastructure.metrics.MicrometerDecisionMetricsAdapter;
import com.bone.claim.infrastructure.notify.LocalNotificationPortAdapter;
import com.bone.claim.infrastructure.rule.LocalRuleEnginePortAdapter;
import com.bone.core.domain.event.DomainEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

          // 7 个智能化出站端口均有实现（防腐层落地，Phase B：AiGateway 接 bone-ai C5 网关）
          assertThat(ctx).hasSingleBean(LocalClaimContextPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalRuleEnginePortAdapter.class);
          assertThat(ctx).hasSingleBean(AiGatewayPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalKnowledgeRetrievalPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalVisualForensicsPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalGraphFraudPortAdapter.class);
          assertThat(ctx).hasSingleBean(LocalNotificationPortAdapter.class);

          // 关键断言：执行器真正收集到 5 个节点（此前为 0 → 假绿）
          DecisionGraphExecutor executor = ctx.getBean(DecisionGraphExecutor.class);
          assertThat(executor.handlers())
              .containsOnlyKeys("EvidenceLoad", "RuleCheck", "AiAssess", "Adjust", "Arbitrate");

          // 可观测性：指标适配器必须真在装配图上，否则"有埋点代码但没人采集"= 生产瞎子
          assertThat(ctx).hasSingleBean(DecisionMetricsPort.class);
          assertThat(ctx).hasSingleBean(MicrometerDecisionMetricsAdapter.class);
        });
  }

  @Configuration
  @ComponentScan(
      basePackages = {
        "com.bone.claim.application.handler",
        "com.bone.claim.infrastructure.context",
        "com.bone.claim.infrastructure.rule",
        "com.bone.claim.infrastructure.ai",
        "com.bone.claim.infrastructure.client",
        "com.bone.claim.infrastructure.knowledge",
        "com.bone.claim.infrastructure.forensics",
        "com.bone.claim.infrastructure.fraud",
        "com.bone.claim.infrastructure.notify",
        "com.bone.claim.infrastructure.metrics"
      })
  @Import(DecisionGraphExecutor.class)
  static class WiringConfig {
    /**
     * 指标注册表：生产由 spring-boot-starter-actuator 自动装配，此处显式提供一个最小注册表， 使装配测试无需拉起完整 Boot
     * 上下文即可验证指标适配器真的进了装配图。
     */
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

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

    // Phase B：提供真实网关门面（默认回落内置 MockLlmClient），使 AiGatewayPortAdapter 可被装配
    @Bean
    LlmGatewayApplicationService llmGatewayApplicationService() {
      return new LlmGatewayApplicationService(
          new MockLlmClient(),
          Mockito.mock(DomainEventPublisher.class),
          (TenantProvider) () -> "t-1",
          LlmGatewayPolicies.permissive(),
          null);
    }
  }
}
