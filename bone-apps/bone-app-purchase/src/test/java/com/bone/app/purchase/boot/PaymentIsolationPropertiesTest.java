package com.bone.app.purchase.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 隔离开关属性判别力测试：缺省必须 false（fail-closed），显式设置才生效。 */
class PaymentIsolationPropertiesTest {

  @Test
  @DisplayName("缺省 enabled=false（fail-closed）")
  void shouldDefaultDisabled() {
    assertThat(new PaymentIsolationProperties().isEnabled()).isFalse();
  }

  @Test
  @DisplayName("显式 enabled=true 生效")
  void shouldReflectExplicitEnable() {
    PaymentIsolationProperties props = new PaymentIsolationProperties();
    props.setEnabled(true);
    assertThat(props.isEnabled()).isTrue();
  }
}
