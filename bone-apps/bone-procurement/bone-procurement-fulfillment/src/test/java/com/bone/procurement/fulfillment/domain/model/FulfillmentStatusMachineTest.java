package com.bone.procurement.fulfillment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.fulfillment.domain.model.asn.AsnStatus;
import com.bone.procurement.fulfillment.domain.model.exception.IrExceptionStatus;
import com.bone.procurement.fulfillment.domain.model.invoice.InvoiceStatus;
import org.junit.jupiter.api.Test;

/** TDD：三个履约聚合的显式迁移表——合法链、非法迁移 fail-closed、终态不可回写、放行边界。 */
class FulfillmentStatusMachineTest {

  @Test
  void ASN合法迁移链() {
    assertThat(AsnStatus.transition(AsnStatus.CREATED, AsnStatus.Event.transmit))
        .isEqualTo(AsnStatus.TRANSMITTED);
    assertThat(AsnStatus.transition(AsnStatus.TRANSMITTED, AsnStatus.Event.goods_received))
        .isEqualTo(AsnStatus.RECEIVING_MATCHED);
    assertThat(AsnStatus.transition(AsnStatus.RECEIVING_MATCHED, AsnStatus.Event.reconciled))
        .isEqualTo(AsnStatus.RECONCILED);
  }

  @Test
  void ASN跳过传输直接对账被拒绝() {
    assertThat(AsnStatus.canTransition(AsnStatus.CREATED, AsnStatus.Event.reconciled)).isFalse();
    assertThatThrownBy(() -> AsnStatus.transition(AsnStatus.CREATED, AsnStatus.Event.reconciled))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid_transition")
        .hasMessageContaining("allowed=");
  }

  @Test
  void ASN终态不可回写() {
    assertThat(AsnStatus.allowedEvents(AsnStatus.CANCELLED)).isEmpty();
    assertThat(AsnStatus.canTransition(AsnStatus.RECONCILED, AsnStatus.Event.cancel)).isFalse();
  }

  @Test
  void 发票997语法回执不置MATCHED() {
    // 997 只能 SUBMITTED → MATCH_PENDING
    assertThat(InvoiceStatus.transition(InvoiceStatus.SUBMITTED, InvoiceStatus.Event.finance_ack))
        .isEqualTo(InvoiceStatus.MATCH_PENDING);
    // MATCH_PENDING 在通用迁移表无任何事件，无法越级到 MATCHED（M5-7）
    assertThat(InvoiceStatus.allowedEvents(InvoiceStatus.MATCH_PENDING)).isEmpty();
    assertThatThrownBy(
            () ->
                InvoiceStatus.transition(
                    InvoiceStatus.MATCH_PENDING, InvoiceStatus.Event.invoice_matched))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 采购域通用迁移表无BLOCKED放行入口() {
    // BLOCKED 只有 exception_raised（补料），没有 released → MATCHED（M5-6）
    assertThat(InvoiceStatus.allowedEvents(InvoiceStatus.BLOCKED))
        .containsExactly(InvoiceStatus.Event.exception_raised);
  }

  @Test
  void 发票合法提交链() {
    assertThat(InvoiceStatus.transition(InvoiceStatus.DRAFT, InvoiceStatus.Event.submit))
        .isEqualTo(InvoiceStatus.SUBMITTED);
  }

  @Test
  void IR例外采购域只能补料不能放行() {
    assertThat(
            IrExceptionStatus.transition(
                IrExceptionStatus.OPEN, IrExceptionStatus.Event.supplement))
        .isEqualTo(IrExceptionStatus.SUPPLEMENTED);
    assertThat(
            IrExceptionStatus.transition(
                IrExceptionStatus.SUPPLEMENTED, IrExceptionStatus.Event.under_review))
        .isEqualTo(IrExceptionStatus.UNDER_REVIEW);
    // UNDER_REVIEW 无 resolve 事件入口，RESOLVED 只能 Finance 回写
    assertThat(IrExceptionStatus.allowedEvents(IrExceptionStatus.UNDER_REVIEW)).isEmpty();
  }
}
