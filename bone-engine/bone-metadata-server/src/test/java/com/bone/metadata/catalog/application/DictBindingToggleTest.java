package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 字典绑定灰度开关单测（界面设计器字典打通 v1.1 §6.5）。
 *
 * <p>判据性质（而非结果）：
 *
 * <ul>
 *   <li><b>优先级可判别</b>：同一租户在「租户白名单开 + 运行时覆盖关」下必须为关，且 source 必须是 runtime ——
 *       只断言布尔值不足以证明覆盖真的生效（配置与覆盖同值时无法区分）。
 *   <li><b>撤销覆盖要回落</b>：clear 后必须回到配置判定，而不是停留在上一次覆盖结果。
 *   <li><b>配置写错必须炸</b>：非法租户 ID 静默忽略 = 该租户静默未开放，灰度名单形同虚设。
 *   <li><b>关闭不是「部分降级」</b>：关闭态视为未绑定（读不解析、写拒绝），见 EnumSourceApplicationServiceTest 的门禁断言。
 * </ul>
 */
class DictBindingToggleTest {

  private static final long TENANT = 7L;

  @Test
  @DisplayName("全局默认关 + 租户不在名单 → 关闭，来源 global-default")
  void defaultOffAndNotListed_disabled() {
    DictBindingToggle toggle = new DictBindingToggle(false, "", () -> TENANT);

    assertThat(toggle.isEnabled(TENANT)).isFalse();
    assertThat(toggle.currentState().source()).isEqualTo(DictBindingToggle.SOURCE_GLOBAL_DEFAULT);
  }

  @Test
  @DisplayName("租户在名单内 → 即使全局默认关也开放，来源 tenant-config")
  void tenantListed_enabledDespiteGlobalOff() {
    DictBindingToggle toggle = new DictBindingToggle(false, "1, 7", () -> TENANT);

    assertThat(toggle.isEnabled(TENANT)).isTrue();
    assertThat(toggle.isEnabled(99L)).isFalse();
    assertThat(toggle.currentState().source()).isEqualTo(DictBindingToggle.SOURCE_TENANT_CONFIG);
  }

  @Test
  @DisplayName("名单含 * → 全部租户开放")
  void wildcard_enablesAllTenants() {
    DictBindingToggle toggle = new DictBindingToggle(false, "*", () -> TENANT);

    assertThat(toggle.isEnabled(0L)).isTrue();
    assertThat(toggle.isEnabled(123456L)).isTrue();
  }

  @Test
  @DisplayName("运行时覆盖优先于租户名单（关），来源 runtime")
  void runtimeOverride_beatsTenantConfig() {
    DictBindingToggle toggle = new DictBindingToggle(false, "7", () -> TENANT);

    DictBindingToggle.ToggleState state = toggle.overrideCurrentTenant(false);

    assertThat(state.enabled()).isFalse();
    assertThat(state.source()).isEqualTo(DictBindingToggle.SOURCE_RUNTIME);
    assertThat(toggle.isEnabledForCurrentTenant()).isFalse();
  }

  @Test
  @DisplayName("运行时覆盖优先于全局默认（开）→ 可即时放开单租户")
  void runtimeOverride_canEnableTenantExcludedByConfig() {
    DictBindingToggle toggle = new DictBindingToggle(false, "", () -> TENANT);

    assertThat(toggle.isEnabled(TENANT)).isFalse();
    toggle.overrideCurrentTenant(true);

    assertThat(toggle.isEnabled(TENANT)).isTrue();
  }

  @Test
  @DisplayName("撤销覆盖 → 回落到配置判定（不停留在上次覆盖值）")
  void clearOverride_fallsBackToConfig() {
    DictBindingToggle toggle = new DictBindingToggle(false, "7", () -> TENANT);
    toggle.overrideCurrentTenant(false);

    DictBindingToggle.ToggleState state = toggle.clearCurrentTenantOverride();

    assertThat(state.enabled()).isTrue();
    assertThat(state.source()).isEqualTo(DictBindingToggle.SOURCE_TENANT_CONFIG);
  }

  @Test
  @DisplayName("配置含非法租户 ID → 启动即失败（不静默当未开放）")
  void illegalTenantToken_failsFast() {
    assertThatThrownBy(() -> new DictBindingToggle(false, "0,abc", () -> TENANT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("abc");
  }

  @Test
  @DisplayName("空白项与多余逗号被忽略，不影响有效项")
  void blankTokensIgnored() {
    DictBindingToggle toggle = new DictBindingToggle(false, " , 7 , ", () -> TENANT);

    assertThat(toggle.isEnabled(TENANT)).isTrue();
  }
}
