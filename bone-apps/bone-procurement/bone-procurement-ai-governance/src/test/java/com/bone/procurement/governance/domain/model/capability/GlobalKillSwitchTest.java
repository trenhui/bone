package com.bone.procurement.governance.domain.model.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 全域 kill switch 单测：缺省全停 fail-closed、一键停用、显式才恢复、审计字段。 */
class GlobalKillSwitchTest {

  @Test
  void 缺省STANDBY不放行AI() {
    GlobalKillSwitch ks = GlobalKillSwitch.standby();
    assertThat(ks.mode()).isEqualTo(GlobalKillSwitch.Mode.STANDBY);
    assertThat(ks.allowsAi()).isFalse();
  }

  @Test
  void 一键停用并记录演练编号() {
    GlobalKillSwitch ks = GlobalKillSwitch.standby();
    ks.engage("admin-1", "应急停机", "DRILL-2026-01");
    assertThat(ks.allowsAi()).isFalse();
    assertThat(ks.operator()).isEqualTo("admin-1");
    assertThat(ks.drillRef()).isEqualTo("DRILL-2026-01");
    assertThat(ks.reason()).isEqualTo("应急停机");
  }

  @Test
  void 仅ENGAGED可显式解除() {
    GlobalKillSwitch ks = GlobalKillSwitch.standby();
    assertThatThrownBy(() -> ks.disengage("admin-1", "绕过"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("只有已停用态");
    ks.engage("admin-1", "停机", "D-1");
    ks.disengage("admin-2", "恢复");
    assertThat(ks.mode()).isEqualTo(GlobalKillSwitch.Mode.DISENGAGED);
    assertThat(ks.allowsAi()).isTrue();
    assertThat(ks.operator()).isEqualTo("admin-2");
  }

  @Test
  void 停机操作缺操作人被拒() {
    GlobalKillSwitch ks = GlobalKillSwitch.standby();
    assertThatThrownBy(() -> ks.engage("", "停机", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("操作人");
  }

  @Test
  void DISENGAGED再停用优先停机() {
    GlobalKillSwitch ks = GlobalKillSwitch.standby();
    ks.engage("admin", "停机", "D-1");
    ks.disengage("admin", "恢复");
    ks.engage("admin", "二次停机", "D-2");
    assertThat(ks.allowsAi()).isFalse();
    assertThat(ks.drillRef()).isEqualTo("D-2");
  }
}
