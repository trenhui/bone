package com.bone.procurement.fulfillment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.procurement.fulfillment.application.command.InvoiceCommand;
import com.bone.procurement.fulfillment.domain.model.invoice.InvoiceSubmission;
import com.bone.procurement.fulfillment.domain.port.FinanceAclPort;
import com.bone.procurement.fulfillment.domain.port.PoQueryPort;
import com.bone.procurement.fulfillment.domain.port.ToleranceConfigPort;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.InvoiceSubmissionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 发票提交服务 TDD：去重(31502)、超余额(31504)、TDS 容差、997 不越级、越权放行拒绝(31505)。 */
class InvoiceSubmissionApplicationServiceTest {

  private InvoiceSubmissionRepository repo;
  private PoQueryPort poQuery;
  private VendorIdentityPort identity;
  private ToleranceConfigPort tolerance;
  private FinanceAclPort financeAcl;
  private InvoiceSubmissionApplicationService service;

  @BeforeEach
  void setUp() {
    repo = mock(InvoiceSubmissionRepository.class);
    poQuery = mock(PoQueryPort.class);
    identity = mock(VendorIdentityPort.class);
    tolerance = mock(ToleranceConfigPort.class);
    financeAcl = mock(FinanceAclPort.class);
    service =
        new InvoiceSubmissionApplicationService(repo, poQuery, identity, tolerance, financeAcl);
    when(identity.requireVendorPartyId()).thenReturn("V-A");
    when(repo.save(any())).thenAnswer(inv -> 1L);
    when(tolerance.tolerance(anyLong(), anyString(), anyString())).thenReturn(BigDecimal.ZERO);
  }

  private PoQueryPort.PoSnapshot poWithBalance(BigDecimal balance) {
    return new PoQueryPort.PoSnapshot(
        "PO-1",
        0,
        "ACKNOWLEDGED",
        "V-A",
        List.of(new PoQueryPort.PoSnapshot.Line(1, "ITEM-1", BigDecimal.ONE, balance, balance)));
  }

  private InvoiceCommand cmd(BigDecimal amount) {
    return new InvoiceCommand(
        1L,
        "INV-1",
        "PO-1",
        amount,
        BigDecimal.ZERO,
        "CNY",
        "CAT-1",
        List.of(new InvoiceCommand.InvoiceLine("ITEM-1", BigDecimal.ONE, amount, amount)));
  }

  @Test
  void 合法发票提交成功() {
    when(repo.findByDedupKey(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
    when(poQuery.findByNo(anyLong(), anyString()))
        .thenReturn(Optional.of(poWithBalance(BigDecimal.TEN)));

    InvoiceSubmission inv = service.submit(cmd(BigDecimal.TEN));

    assertThat(inv.getStatus()).isEqualTo("DRAFT");
    assertThat(inv.getInvoiceNo()).isEqualTo("INV-1");
  }

  @Test
  void 重复发票号被拒绝31502() {
    InvoiceSubmission dup = new InvoiceSubmission();
    when(repo.findByDedupKey(anyLong(), anyString(), anyString())).thenReturn(Optional.of(dup));

    assertThatThrownBy(() -> service.submit(cmd(BigDecimal.TEN)))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("31502");
    verify(repo, never()).save(any());
  }

  @Test
  void 发票超未开票余额被拒绝31504() {
    when(repo.findByDedupKey(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
    when(poQuery.findByNo(anyLong(), anyString()))
        .thenReturn(Optional.of(poWithBalance(BigDecimal.ONE)));

    assertThatThrownBy(() -> service.submit(cmd(BigDecimal.TEN)))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("31504");
  }

  @Test
  void TDS超容差被拒收() {
    when(repo.findByDedupKey(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
    when(tolerance.tolerance(anyLong(), anyString(), anyString()))
        .thenReturn(BigDecimal.valueOf(1));
    when(poQuery.findByNo(anyLong(), anyString()))
        .thenReturn(Optional.of(poWithBalance(BigDecimal.valueOf(100))));
    // amount=10 与行合计=10 一致，但让行合计固定为 amount → 无差；构造 TDS 差额：amount 与 lines 不同
    InvoiceCommand mismatch =
        new InvoiceCommand(
            1L,
            "INV-1",
            "PO-1",
            BigDecimal.valueOf(20),
            BigDecimal.ZERO,
            "CNY",
            "CAT-1",
            List.of(
                new InvoiceCommand.InvoiceLine(
                    "ITEM-1", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN)));

    assertThatThrownBy(() -> service.submit(mismatch))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("TDS 差额");
  }

  @Test
  void 语法回执只推进到MATCHPENDING不置MATCHED() {
    InvoiceSubmission submitted = new InvoiceSubmission();
    submitted.setStatus("SUBMITTED");
    submitted.setVendorPartyId("V-A");
    when(repo.findByDedupKey(anyLong(), anyString(), anyString()))
        .thenReturn(Optional.of(submitted));

    InvoiceSubmission result = service.applySyntaxAck(1L, "INV-1");

    assertThat(result.getStatus()).isEqualTo("MATCH_PENDING");
  }

  @Test
  void 采购域越权放行被拒绝31505() {
    assertThatThrownBy(() -> service.rejectProcurementRelease())
        .isInstanceOf(BizException.class)
        .hasMessageContaining("31505");
  }

  @Test
  void Finance不可用时匹配结果保持待态() {
    InvoiceSubmission pending = new InvoiceSubmission();
    pending.setStatus("MATCH_PENDING");
    when(repo.findByDedupKey(anyLong(), anyString(), anyString())).thenReturn(Optional.of(pending));
    when(financeAcl.isAvailable()).thenReturn(false);

    InvoiceSubmission result = service.applyFinanceResult(1L, "INV-1", "V-A", "MATCHED", "evt-1");

    assertThat(result.getStatus()).isEqualTo("MATCH_PENDING");
    verify(repo, never()).save(any());
  }
}
