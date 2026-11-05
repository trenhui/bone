package com.bone.claim.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.infrastructure.ai.LocalAiGatewayPortAdapter;
import com.bone.claim.infrastructure.client.AiGatewayPortAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * AI 网关端口的装配唯一性（默认真实网关 / local 模式本地桩）。
 *
 * <p><b>为什么需要这组用例</b>：两个适配器满足同一端口，真实实现是无条件 {@code @Component}，本地桩由 {@code
 * synap.claim.ai.gateway.mode=local} 打开。开关一旦打开就是<b>两个 Bean 并存</b>，而注入点（{@code
 * AiAssessNodeHandler}）按类型注入、没有 {@code @Qualifier}——没有 {@code @Primary} 时启动即 {@code
 * NoUniqueBeanDefinitionException}。也就是说"本地模式"这个开关<b>唯一存在的场景恰恰是它失效的场景</b>，且只在 手动打开开关时才会暴露，常规 CI
 * 永远看不到。
 *
 * <p><b>为什么用 {@link ApplicationContextRunner} 而不是 {@code @SpringBootTest}</b>：要验证的是"条件装配 + 按类型解析"
 * 这一小段容器行为，不需要数据库、Nacos 与整个应用上下文；把断言限定在这两个 Bean 上，失败原因才唯一。
 */
class AiGatewayPortWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(
              LlmGatewayApplicationService.class, () -> mock(LlmGatewayApplicationService.class))
          .withBean(
              com.bone.ai.gateway.config.LlmGatewayProperties.class,
              com.bone.ai.gateway.config.LlmGatewayProperties::new)
          .withUserConfiguration(AiGatewayPortAdapter.class, LocalAiGatewayPortAdapter.class);

  @Test
  @DisplayName("默认（无 mode 属性）⇒ 只有真实网关适配器，本地桩不参与装配")
  void defaultModeUsesRealGatewayOnly() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(AiGatewayPort.class);
          assertThat(ctx.getBean(AiGatewayPort.class))
              .as("默认必须委托 bone-ai C5 网关（PII 脱敏 / 注入检测 / 证据链），不得回落到本地桩")
              .isInstanceOf(AiGatewayPortAdapter.class);
        });
  }

  @Test
  @DisplayName("mode=local ⇒ 两 Bean 并存但按类型注入取到本地桩（无 @Primary 此处即 NoUniqueBeanDefinitionException）")
  void localModePrefersLocalStub() {
    runner
        .withPropertyValues("synap.claim.ai.gateway.mode=local")
        .run(
            ctx -> {
              // 断言"两个都在"而非"只剩一个"：本用例锁的是解析结果可预期，不是消灭真实适配器
              assertThat(ctx.getBeansOfType(AiGatewayPort.class)).hasSize(2);
              assertThat(ctx.getBean(AiGatewayPort.class))
                  .as("删除本地桩上的 @Primary，这一行会抛 NoUniqueBeanDefinitionException——即本地模式起不来")
                  .isInstanceOf(LocalAiGatewayPortAdapter.class);
            });
  }
}
