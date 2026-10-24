package com.bone.aiorch.domain.hitl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.aiorch.domain.decision.Confirmed;
import org.junit.jupiter.api.Test;

/**
 * HITL 终态项 → {@link Confirmed}（类型护栏消费端，详设 §3.3 规则 6）：只有裁决通过的项携带确认结论。
 *
 * <p>锁两条防回归契约：① 未裁 / 驳回 / 升级项取确认 ⇒ 显式失败（拿未裁项当确认用是审计事故）；② 确认结论必带 裁决人与 traceId（回放关联）。
 */
class HitlItemConfirmedTest {

  private static HitlItem pendingItem() {
    return HitlItem.create("1", "claim-1001", "建议赔付 1000 元", "1000", "LOW", 0.9, "trace-1");
  }

  @Test
  void pending_item_has_no_confirmed_decision() {
    assertThatThrownBy(() -> pendingItem().toConfirmed())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CONFIRMED/MODIFIED");
  }

  @Test
  void rejected_item_has_no_confirmed_decision() {
    HitlItem item = pendingItem();
    item.reject("reviewer:tom", ReasonType.SUGGESTION_WRONG, "证据不足");
    assertThatThrownBy(() -> item.toConfirmed()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void confirmed_item_carries_audited_decision() {
    HitlItem item = pendingItem();
    item.confirm("reviewer:tom", null);
    Confirmed<String> confirmed = item.toConfirmed();
    assertThat(confirmed.value()).isEqualTo("1000");
    assertThat(confirmed.decidedBy()).isEqualTo("reviewer:tom");
    assertThat(confirmed.evidenceRef()).isEqualTo("trace-1");
    assertThat(confirmed.at()).isNotNull();
  }

  @Test
  void modified_item_carries_corrected_value() {
    HitlItem item = pendingItem();
    item.modify("reviewer:tom", "800", ReasonType.SUGGESTION_WRONG, "按条款下调");
    Confirmed<String> confirmed = item.toConfirmed();
    assertThat(confirmed.value()).isEqualTo("800");
    assertThat(confirmed.decidedBy()).isEqualTo("reviewer:tom");
  }
}
