package com.bone.procurement.transaction.domain.model.requisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** TDD RED：显式迁移表——合法链通过，跳步/终态回写/取消后迁移全部拒绝。 */
class RequisitionStatusTest {

  @Test
  void 合法迁移链通过() {
    assertThat(RequisitionStatus.canTransition(RequisitionStatus.DRAFT, RequisitionEvent.SUBMIT))
        .isTrue();
    assertThat(
            RequisitionStatus.canTransition(RequisitionStatus.SUBMITTED, RequisitionEvent.APPROVE))
        .isTrue();
    assertThat(
            RequisitionStatus.canTransition(RequisitionStatus.APPROVED, RequisitionEvent.CONVERT))
        .isTrue();
  }

  @Test
  void DRAFT直接转单被拒绝() {
    assertThat(RequisitionStatus.canTransition(RequisitionStatus.DRAFT, RequisitionEvent.CONVERT))
        .isFalse();
  }

  @Test
  void 终态CONVERTED不可回写() {
    assertThat(
            RequisitionStatus.canTransition(RequisitionStatus.CONVERTED, RequisitionEvent.SUBMIT))
        .isFalse();
  }

  @Test
  void CANCELLED后再提交被拒绝() {
    assertThat(
            RequisitionStatus.canTransition(RequisitionStatus.CANCELLED, RequisitionEvent.SUBMIT))
        .isFalse();
  }

  @Test
  void DRAFT_SUBMITTED_APPROVED均可取消() {
    assertThat(RequisitionStatus.canTransition(RequisitionStatus.DRAFT, RequisitionEvent.CANCEL))
        .isTrue();
    assertThat(
            RequisitionStatus.canTransition(RequisitionStatus.SUBMITTED, RequisitionEvent.CANCEL))
        .isTrue();
    assertThat(RequisitionStatus.canTransition(RequisitionStatus.APPROVED, RequisitionEvent.CANCEL))
        .isTrue();
  }

  @Test
  void 非法迁移抛异常并带当前态事件与允许集() {
    assertThatThrownBy(
            () -> RequisitionStatus.transition(RequisitionStatus.DRAFT, RequisitionEvent.CONVERT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DRAFT")
        .hasMessageContaining("CONVERT");
  }
}
