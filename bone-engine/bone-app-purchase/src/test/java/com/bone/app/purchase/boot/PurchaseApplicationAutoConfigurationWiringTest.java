package com.bone.app.purchase.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.app.purchase.PurchaseOrderPayHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 装配验证：采购应用的付款处理器经 autoconfig 注册为 bean。 */
class PurchaseApplicationAutoConfigurationWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PurchaseApplicationAutoConfiguration.class));

  @Test
  void registersPurchaseComponents() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(PurchaseOrderPayHandler.class));
  }
}
