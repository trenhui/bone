package com.bone.app.purchase.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.app.purchase.boot.PurchaseComponentScanConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 采购审批流接入装配验证：四动作 Handler + 状态投影端口被组件扫描注册（不依赖引擎本体）。 */
class PurchaseWorkflowWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ScanConfig.class);

  @Test
  void registersWorkflowBeans() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasBean("purchaseWorkflowSubmitHandler");
          assertThat(ctx).hasBean("purchaseWorkflowDecisionHandler");
          assertThat(ctx).hasBean("purchaseWorkflowStatusPort");
        });
  }

  @Configuration
  static class ScanConfig extends PurchaseComponentScanConfiguration {}
}
