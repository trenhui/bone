package com.bone.procurement.fulfillment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.procurement.fulfillment.domain.model.exception.IrException;
import com.bone.procurement.fulfillment.domain.model.invoice.InvoiceSubmission;
import com.bone.procurement.fulfillment.domain.port.FinanceAclPort;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.InvoiceSubmissionRepository;
import com.bone.procurement.fulfillment.domain.repository.IrExceptionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** IR 例外服务 TDD：幂等登记、补料、Finance 回写唯一来源、越权放行拒绝(31505)。 */
class IrExceptionServiceTest {

  private IrExceptionRepository repo;
  private InvoiceSubmissionRepository invoiceRepo;
  private VendorIdentityPort identity;
  private FinanceAclPort financeAcl;
  private IrExceptionService service;

  @BeforeEach
  void setUp() {
    repo = mock(IrExceptionRepository.class);
    invoiceRepo = mock(InvoiceSubmissionRepository.class);
    identity = mock(VendorIdentityPort.class);
    financeAcl = mock(FinanceAclPort.class);
    service = new IrExceptionService(repo, invoiceRepo, identity, financeAcl);
    when(identity.requireVendorPartyId()).thenReturn("V-A");
    when(repo.save(any())).thenAnswer(inv -> 1L);
  }

  @Test
  void 匹配差异生成OPEN例外() {
    when(repo.findByDedupKey(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());

    IrException ex = service.raise(1L, "EX-1", "INV-1", "PRICE_VARIANCE", "EXCEPTION");

    assertThat(ex.getStatus()).isEqualTo("OPEN");
    assertThat(ex.getExceptionNo()).isEqualTo("EX-1");
  }

  @Test
  void 重复异常码幂等不复写() {
    IrException existing = new IrException();
    existing.setExceptionNo("EX-1");
    when(repo.findByDedupKey(anyLong(), anyString(), anyString()))
        .thenReturn(Optional.of(existing));

    IrException ex = service.raise(1L, "EX-1", "INV-1", "PRICE_VARIANCE", "EXCEPTION");

    assertThat(ex.getExceptionNo()).isEqualTo("EX-1");
    verify(repo, never()).save(any());
  }

  @Test
  void 补料推进到SUPPLEMENTED() {
    IrException open = new IrException();
    open.setExceptionNo("EX-1");
    open.setInvoiceNo("INV-1");
    open.setStatus("OPEN");
    when(repo.findByExceptionNo(anyLong(), anyString())).thenReturn(Optional.of(open));
    InvoiceSubmission inv = new InvoiceSubmission();
    inv.setInvoiceNo("INV-1");
    inv.setVendorPartyId("V-A");
    when(invoiceRepo.findPageByTenant(anyLong(), anyInt(), anyInt()))
        .thenReturn(PageResult.of(List.of(inv), 1L, 1, 200));

    IrException result = service.supplement(1L, "EX-1", "SUPP-1");

    assertThat(result.getStatus()).isEqualTo("SUPPLEMENTED");
  }

  @Test
  void 采购域越权放行被拒绝31505() {
    assertThatThrownBy(() -> service.rejectProcurementRelease())
        .isInstanceOf(BizException.class)
        .hasMessageContaining("31505");
  }

  @Test
  void Finance不可用时例外不放行() {
    IrException underReview = new IrException();
    underReview.setStatus("UNDER_REVIEW");
    when(repo.findByExceptionNo(anyLong(), anyString())).thenReturn(Optional.of(underReview));
    when(financeAcl.isAvailable()).thenReturn(false);

    IrException result = service.applyFinanceDecision(1L, "EX-1", true, "evt-2");

    assertThat(result.getStatus()).isEqualTo("UNDER_REVIEW");
    verify(repo, never()).save(any());
  }

  @Test
  void Finance事件回写RESOLVED() {
    IrException underReview = new IrException();
    underReview.setStatus("UNDER_REVIEW");
    when(repo.findByExceptionNo(anyLong(), anyString())).thenReturn(Optional.of(underReview));
    when(financeAcl.isAvailable()).thenReturn(true);

    IrException result = service.applyFinanceDecision(1L, "EX-1", true, "evt-2");

    assertThat(result.getStatus()).isEqualTo("RESOLVED");
    assertThat(result.getFinanceEventId()).isEqualTo("evt-2");
  }
}
