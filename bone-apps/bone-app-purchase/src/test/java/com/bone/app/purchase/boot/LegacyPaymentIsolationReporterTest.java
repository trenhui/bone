package com.bone.app.purchase.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 启动期隔离日志的可观察证据测试（D-06）：禁用态与启用态日志措辞可判别。 */
@ExtendWith(OutputCaptureExtension.class)
class LegacyPaymentIsolationReporterTest {

  @Test
  @DisplayName("禁用态日志含硬隔离与 PaymentRequested 提示")
  void shouldLogHardIsolation_whenDisabled(CapturedOutput out) {
    PaymentIsolationProperties props = new PaymentIsolationProperties(); // 缺省 false
    new LegacyPaymentIsolationReporter(props).report();

    assertThat(out.getOut()).contains("硬隔离/禁用").contains("PaymentRequested");
  }

  @Test
  @DisplayName("启用态日志含仅演示与禁止生产出金提示")
  void shouldLogDemoOnly_whenEnabled(CapturedOutput out) {
    PaymentIsolationProperties props = new PaymentIsolationProperties();
    props.setEnabled(true);
    new LegacyPaymentIsolationReporter(props).report();

    assertThat(out.getOut()).contains("演示/占位").contains("禁止用于生产出金");
  }
}
