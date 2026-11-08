package com.bone.claim.domain.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 特性开关状态取值的收紧测试（{@code FeatureToggle.state}）。
 *
 * <p><b>为什么是这组用例</b>：{@code state} 是自由 {@code varchar}，而 {@link FeatureToggle#isOn(long)} 只认 {@code
 * ENABLED}。于是任何拼写错误（{@code ENABLE} / {@code enabled}）或未来新增的档位（契约 §11.4 的 AI 介入三档 {@code
 * SHADOW/ASSISTED/AUTOMATED}）都会<b>静默当成关闭</b>——而「关闭」是 fail-closed 的安全方向，于是：
 *
 * <ul>
 *   <li>配置者以为开关已开，实际从未生效；
 *   <li>因为方向是「安全」的，<b>没有任何报错、没有任何痕迹</b>，只能靠业务方报「功能没反应」才发现。
 * </ul>
 *
 * <p>这类「配了不生效且无人知晓」的缺陷比直接报错更难发现，故按<b>双层防御</b>收紧：
 *
 * <ol>
 *   <li><b>写入期拒绝</b>：{@link FeatureToggle#create} 对非法状态直接抛异常，且消息带<b>后果</b>（不只说「不合法」）；
 *   <li><b>读取期兜底保留</b>：{@link FeatureToggle#isOn(long)} 仍对未知状态当关闭——这一条<b>不能</b>收紧，
 *       收紧成「未知值交给上层判断」会把安全方向倒过来；改为用 {@link FeatureToggle#hasSupportedState()} 把「配错了」暴露给调用方（应用层打
 *       WARN）。
 * </ol>
 *
 * <p><b>变异判别力</b>：删掉写入期的状态校验 → {@link #create_rejectsUnsupportedState()} 转红；把读取期改成 「未知值按开启」 →
 * {@link #isOn_unknownState_staysClosed()} 转红；删掉 {@code hasSupportedState} → {@link
 * #hasSupportedState_flagsUnknownValue()} 转红。
 */
class FeatureToggleStateTest {

  private static final Long TENANT = 7L;
  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

  @Test
  @DisplayName("写入期：合法状态可创建")
  void create_acceptsSupportedStates() {
    FeatureToggle on = FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", 100, "", NOW);
    FeatureToggle off = FeatureToggle.create(TENANT, "AUTO_APPROVE", "DISABLED", 100, "", NOW);

    assertThat(on.getState()).isEqualTo("ENABLED");
    assertThat(off.getState()).isEqualTo("DISABLED");
  }

  @Test
  @DisplayName("写入期：非法状态被拒绝（拼写错误 / AI 三档 / null）")
  void create_rejectsUnsupportedState() {
    for (String bad :
        new String[] {"ENABLE", "enabled", "Enabled", "AUTOMATED", "SHADOW", "ASSISTED", null}) {
      assertThatThrownBy(() -> FeatureToggle.create(TENANT, "AUTO_APPROVE", bad, 100, "", NOW))
          .as("非法状态 `%s` 必须在写入期被拒绝", bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("state")
          .hasMessageContaining("静默当成关闭");
    }
  }

  @Test
  @DisplayName("写入期：错误消息必须含后果（不只说「不合法」）")
  void create_errorMessageContainsConsequence() {
    assertThatThrownBy(() -> FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLE", 100, "", NOW))
        .hasMessageContaining("静默当成关闭")
        .hasMessageContaining("ENABLED")
        .hasMessageContaining("SHADOW/ASSISTED/AUTOMATED");
  }

  @Test
  @DisplayName("写入期：rollout_pct 越界被拒绝（0-100）")
  void create_rejectsOutOfRangeRolloutPct() {
    assertThatThrownBy(() -> FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", 101, "", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rollout_pct");
    assertThatThrownBy(() -> FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", -1, "", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rollout_pct");
  }

  @Test
  @DisplayName("读取期：未知状态仍按关闭处理（fail-closed 不得被收紧成「未知即开」）")
  void isOn_unknownState_staysClosed() {
    // 构造非法状态（绕过写入期校验，模拟历史脏数据 / 直连写入）
    FeatureToggle toggle = FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", 100, "", NOW);
    setState(toggle, "AUTOMATED");

    assertThat(toggle.isOn(TENANT)).as("未知状态必须按关闭处理——收紧成「未知即开」会把安全方向倒过来").isFalse();
  }

  @Test
  @DisplayName("读取期：未知状态可被识别（供应用层打 WARN，让「配错了」可见）")
  void hasSupportedState_flagsUnknownValue() {
    FeatureToggle good = FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", 100, "", NOW);
    FeatureToggle bad = FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", 100, "", NOW);
    setState(bad, "ENABLE");

    assertThat(good.hasSupportedState()).isTrue();
    assertThat(bad.hasSupportedState()).isFalse();
  }

  @Test
  @DisplayName("读取期：ENABLED 且灰度命中才为开")
  void isOn_respectsRolloutPct() {
    FeatureToggle full = FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", 100, "", NOW);
    FeatureToggle none = FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", 0, "", NOW);
    FeatureToggle half = FeatureToggle.create(TENANT, "AUTO_APPROVE", "ENABLED", 50, "", NOW);

    assertThat(full.isOn(1L)).isTrue();
    assertThat(none.isOn(1L)).isFalse();
    // 灰度按 floorMod(tenantId,100) 判定：tenantId=10 → 10 < 50 命中；tenantId=90 → 90 < 50 不命中
    assertThat(half.isOn(10L)).isTrue();
    assertThat(half.isOn(90L)).isFalse();
  }

  /** 反射改写 state：模拟绕过写入期校验的脏数据（历史存量 / 直连写库）。 */
  private static void setState(FeatureToggle toggle, String state) {
    try {
      var field = FeatureToggle.class.getDeclaredField("state");
      field.setAccessible(true);
      field.set(toggle, state);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("反射改写 state 失败", e);
    }
  }
}
