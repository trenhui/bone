package com.bone.procurement.transaction.domain.model.po;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** TDD RED：PO 显式迁移表——合法链、跳步拒绝、终态回写拒绝、保留枚举不可在本期推进。 */
class PoStatusTest {

  @Test
  void 合法迁移链通过() {
    assertThat(PoStatus.canTransition(PoStatus.DRAFT, PoEvent.SUBMIT)).isTrue();
    assertThat(PoStatus.canTransition(PoStatus.SUBMITTED, PoEvent.APPROVE)).isTrue();
    assertThat(PoStatus.canTransition(PoStatus.APPROVED, PoEvent.SEND)).isTrue();
  }

  @Test
  void DRAFT直接SENT被拒绝() {
    assertThat(PoStatus.canTransition(PoStatus.DRAFT, PoEvent.SEND)).isFalse();
    assertThatThrownBy(() -> PoStatus.transition(PoStatus.DRAFT, PoEvent.SEND))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DRAFT")
        .hasMessageContaining("SEND");
  }

  @Test
  void CLOSED与CANCELLED终态回写被拒绝() {
    assertThat(PoStatus.canTransition(PoStatus.CLOSED, PoEvent.SEND)).isFalse();
    assertThat(PoStatus.canTransition(PoStatus.CANCELLED, PoEvent.SUBMIT)).isFalse();
  }

  @Test
  void 履约保留状态本期无推进入口() {
    for (PoStatus s :
        new PoStatus[] {
          PoStatus.SENT, PoStatus.ACKNOWLEDGED, PoStatus.PARTIAL_RECEIVED, PoStatus.FULLY_RECEIVED
        }) {
      assertThat(PoStatus.allowedEvents(s)).isEmpty();
    }
  }

  @Test
  void 非法迁移错误体携带允许事件集() {
    assertThatThrownBy(() -> PoStatus.transition(PoStatus.DRAFT, PoEvent.SEND))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("allowed=");
  }
}
