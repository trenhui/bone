package com.bone.app.purchase.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.app.purchase.PurchaseOrderPayHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 装配验证（D-06 硬隔离）：存量付款处理器缺省不注册，仅显式开启才注册。
 *
 * <p>判别力：缺省 / 关闭态断言 doesNotHaveBean，显式 true 断言 hasSingleBean；两态互斥，删掉条件装配或 REGEX 排除任一处都会转红。
 */
class PurchaseApplicationAutoConfigurationWiringTest {

  private static final String ENABLE_KEY = "procura.purchase-pay-handler.enabled";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PurchaseApplicationAutoConfiguration.class));

  @Test
  @DisplayName("缺省配置 → 付款处理器不注册（fail-closed），但隔离报告器始终注册")
  void shouldNotRegisterPayHandler_whenSwitchAbsent() {
    runner.run(
        ctx -> {
          assertThat(ctx).doesNotHaveBean(PurchaseOrderPayHandler.class);
          // 防假绿：报告器必须在装配链上，删掉 @Bean 声明会转红（日志测试单独 new 无法捕获）。
          assertThat(ctx).hasSingleBean(LegacyPaymentIsolationReporter.class);
        });
  }

  @Test
  @DisplayName("显式关闭 → 付款处理器不注册")
  void shouldNotRegisterPayHandler_whenSwitchFalse() {
    runner
        .withPropertyValues(ENABLE_KEY + "=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(PurchaseOrderPayHandler.class));
  }

  @Test
  @DisplayName("显式开启 → 付款处理器注册且仅一个（演示占位）")
  void shouldRegisterSinglePayHandler_whenSwitchTrue() {
    runner
        .withPropertyValues(ENABLE_KEY + "=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(PurchaseOrderPayHandler.class));
  }
}
