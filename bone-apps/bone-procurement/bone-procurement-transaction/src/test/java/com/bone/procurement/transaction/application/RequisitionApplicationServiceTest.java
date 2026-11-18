package com.bone.procurement.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bone.core.exception.BizException;
import com.bone.procurement.transaction.application.command.CreateRequisitionCommand;
import com.bone.procurement.transaction.domain.model.requisition.Requisition;
import com.bone.procurement.transaction.domain.model.requisition.RequisitionStatus;
import com.bone.procurement.transaction.domain.repository.RequisitionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TDD RED：请购单生命周期——建单、提交、审批、转单前提、转单失败状态不变、终态保护。 */
@ExtendWith(MockitoExtension.class)
class RequisitionApplicationServiceTest {

  @Mock RequisitionRepository repo;
  @Mock OrderNoGenerator noGen;
  @Mock PurchaseOrderApplicationService poService;

  private RequisitionApplicationService service;
  private final Long tenant = 1L;

  @BeforeEach
  void setUp() {
    service = new RequisitionApplicationService(repo, noGen, poService);
  }

  private Requisition requisition(RequisitionStatus status) {
    Requisition r = new Requisition();
    r.setTenantId(tenant);
    r.setPrNo("PR-2026-000001");
    r.setStatus(status.name());
    r.setVersion(0);
    return r;
  }

  @Test
  void 建单生成DRAFT状态请购单() {
    when(noGen.nextPoRequisitionNo(tenant)).thenReturn("PR-2026-000001");
    when(repo.save(any())).thenAnswer(inv -> 1L);
    Requisition r =
        service.create(
            new CreateRequisitionCommand(tenant, 2L, 3L, new BigDecimal("100.0000"), "CNY", null));
    assertThat(r.getStatus()).isEqualTo(RequisitionStatus.DRAFT.name());
    assertThat(r.getPrNo()).isEqualTo("PR-2026-000001");
  }

  @Test
  void 未审批转单被拒且状态不变() {
    Requisition r = requisition(RequisitionStatus.SUBMITTED);
    when(repo.findByNo(tenant, "PR-2026-000001")).thenReturn(Optional.of(r));
    assertThatThrownBy(() -> service.convert(tenant, "PR-2026-000001", "SUP-1", 100L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid_transition");
    assertThat(r.getStatus()).isEqualTo(RequisitionStatus.SUBMITTED.name());
    verify(repo, never()).save(any());
  }

  @Test
  void 转单失败供应商未开通时请购单保持APPROVED() {
    Requisition r = requisition(RequisitionStatus.APPROVED);
    when(repo.findByNo(tenant, "PR-2026-000001")).thenReturn(Optional.of(r));
    when(poService.create(any())).thenThrow(new BizException(400, "供应商未开通", "31003"));
    // convert 内部委托 PO 服务，失败时 PR 不得落库也不得改状态
    assertThatThrownBy(() -> service.convert(tenant, "PR-2026-000001", "SUP-1", 100L))
        .isInstanceOf(BizException.class);
    assertThat(r.getStatus()).isEqualTo(RequisitionStatus.APPROVED.name());
    verify(repo, never()).save(any());
  }

  @Test
  void CONVERTED终态回写被拒() {
    Requisition r = requisition(RequisitionStatus.CONVERTED);
    when(repo.findByNo(tenant, "PR-2026-000001")).thenReturn(Optional.of(r));
    assertThatThrownBy(() -> service.submit(tenant, "PR-2026-000001"))
        .isInstanceOf(IllegalStateException.class);
  }
}
