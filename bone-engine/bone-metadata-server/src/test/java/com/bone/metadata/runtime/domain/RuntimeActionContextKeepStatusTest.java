package com.bone.metadata.runtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「动作不推进平台状态」判据（{@link RuntimeActionContext#keepsPlatformStatus}）的契约测试。
 *
 * <p>为什么单测这个字符串比较：它决定单据 status 是否被推到动作定义的 {@code to_status}。拼错常量或宽松匹配 会让「退回节点返工」的单据被推进到 REJECTED
 * 终态而实例仍在审批中——实测死单形态（再点通过恒 409、待办清不掉）。 故真值表必须钉住：只认 KEEP（大小写/首尾空格容错），其余一律视为「照常推进」。
 */
class RuntimeActionContextKeepStatusTest {

  private static Map<String, Object> sideEffect(Object value) {
    Map<String, Object> map = new HashMap<>();
    if (value != null) {
      map.put(RuntimeActionContext.OUTCOME_PLATFORM_STATUS, value);
    }
    return map;
  }

  @Test
  @DisplayName("null / 空副作用 → 照常推进状态")
  void nullSideEffect_advances() {
    assertThat(RuntimeActionContext.keepsPlatformStatus(null)).isFalse();
    assertThat(RuntimeActionContext.keepsPlatformStatus(Map.of())).isFalse();
  }

  @Test
  @DisplayName("KEEP（含大小写与首尾空格）→ 不推进状态")
  void keepValues_holdStatus() {
    assertThat(RuntimeActionContext.keepsPlatformStatus(sideEffect("KEEP"))).isTrue();
    assertThat(RuntimeActionContext.keepsPlatformStatus(sideEffect("keep"))).isTrue();
    assertThat(RuntimeActionContext.keepsPlatformStatus(sideEffect("  Keep "))).isTrue();
  }

  @Test
  @DisplayName("其它取值一律照常推进（不得因拼写相近而误判）")
  void otherValues_advance() {
    assertThat(RuntimeActionContext.keepsPlatformStatus(sideEffect("KEEPER"))).isFalse();
    assertThat(RuntimeActionContext.keepsPlatformStatus(sideEffect("keepStatus"))).isFalse();
    assertThat(RuntimeActionContext.keepsPlatformStatus(sideEffect(""))).isFalse();
    assertThat(RuntimeActionContext.keepsPlatformStatus(sideEffect(0))).isFalse();
  }
}
