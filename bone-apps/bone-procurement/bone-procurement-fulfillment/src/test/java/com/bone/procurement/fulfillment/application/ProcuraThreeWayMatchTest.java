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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** evalset MATCH-01/02 源码落点：三单差额超容差阻断自动通过；缺失/不可信 Finance 匹配结果保持待人工态。 */
class ProcuraThreeWayMatchTest {

  private InvoiceSubmissionRepository repo;
  private PoQueryPort poQuery;
  private ToleranceConfigPort tolerance;
  private FinanceAclPort financeAcl;
  private InvoiceSubmissionApplicationService service;

  @BeforeEach
  void setUp() {
    repo = mock(InvoiceSubmissionRepository.class);
    poQuery = mock(PoQueryPort.class);
    VendorIdentityPort identity = mock(VendorIdentityPort.class);
    tolerance = mock(ToleranceConfigPort.class);
    financeAcl = mock(FinanceAclPort.class);
    service =
        new InvoiceSubmissionApplicationService(repo, poQuery, identity, tolerance, financeAcl);
    when(identity.requireVendorPartyId()).thenReturn("V-A");
    when(repo.save(any())).thenAnswer(inv -> 1L);
  }

  @Test
  @DisplayName("amountVarianceOverTolerance_blocksAutoPass")
  void amountVarianceOverTolerance_blocksAutoPass() {
    when(repo.findByDedupKey(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
    when(tolerance.tolerance(anyLong(), anyString(), anyString()))
        .thenReturn(BigDecimal.valueOf(1));
    when(poQuery.findByNo(anyLong(), anyString()))
        .thenReturn(Optional.of(poWithBalance(BigDecimal.valueOf(100))));

    InvoiceCommand mismatch =
        new InvoiceCommand(
            1L,
            "INV-MATCH-1",
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
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("missingMatchStatus_fallsBackToManual")
  void missingMatchStatus_fallsBackToManual() {
    InvoiceSubmission pending = new InvoiceSubmission();
    pending.setStatus("MATCH_PENDING");
    when(repo.findByDedupKey(anyLong(), anyString(), anyString())).thenReturn(Optional.of(pending));
    when(financeAcl.isAvailable()).thenReturn(false);

    InvoiceSubmission result = service.applyFinanceResult(1L, "INV-MATCH-2", "V-A", null, null);

    assertThat(result.getStatus()).isEqualTo("MATCH_PENDING");
    assertThat(result.getFinanceEventId()).isNull();
    verify(repo, never()).save(any());
  }

  private static PoQueryPort.PoSnapshot poWithBalance(BigDecimal balance) {
    return new PoQueryPort.PoSnapshot(
        "PO-1",
        0,
        "ACKNOWLEDGED",
        "V-A",
        List.of(new PoQueryPort.PoSnapshot.Line(1, "ITEM-1", BigDecimal.ONE, balance, balance)));
  }
}
