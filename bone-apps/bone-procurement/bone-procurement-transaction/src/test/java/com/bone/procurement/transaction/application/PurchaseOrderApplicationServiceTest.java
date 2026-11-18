package com.bone.procurement.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.procurement.transaction.application.command.CreatePurchaseOrderCommand;
import com.bone.procurement.transaction.domain.ProcurementErrorCodes;
import com.bone.procurement.transaction.domain.model.po.PoStatus;
import com.bone.procurement.transaction.domain.model.po.PurchaseOrder;
import com.bone.procurement.transaction.domain.port.VendorQueryPort;
import com.bone.procurement.transaction.domain.repository.PurchaseOrderLineRepository;
import com.bone.procurement.transaction.domain.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TDD RED：PO 建单闸门负向探针 M4-1/2/5 + 终态回写 M4-6 + 缺快照 M4-10。 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderApplicationServiceTest {

  @Mock PurchaseOrderRepository poRepo;
  @Mock PurchaseOrderLineRepository lineRepo;
  @Mock VendorQueryPort vendorPort;
  @Mock OrderNoGenerator noGen;

  private PurchaseOrderApplicationService service;
  private final Long tenant = 1L;

  @BeforeEach
  void setUp() {
    service = new PurchaseOrderApplicationService(poRepo, lineRepo, vendorPort, noGen);
  }

  private CreatePurchaseOrderCommand cmd() {
    return new CreatePurchaseOrderCommand(
        tenant,
        1L,
        "SUP-1",
        100L,
        null,
        null,
        null,
        null,
        "TAX-V2",
        null,
        List.of(
            new CreatePurchaseOrderCommand.Line(
                "ITEM-1",
                "物料1",
                new BigDecimal("2"),
                "个",
                new BigDecimal("10.0000"),
                new BigDecimal("0.13")),
            new CreatePurchaseOrderCommand.Line(
                "ITEM-2",
                "物料2",
                new BigDecimal("1"),
                "个",
                new BigDecimal("5.5000"),
                new BigDecimal("0.13"))));
  }

  private void activeVendor(boolean isActive) {
    when(vendorPort.findSnapshot(tenant, "SUP-1"))
        .thenReturn(
            Optional.of(
                new VendorQueryPort.VendorSnapshot(
                    "SUP-1", isActive ? "ACTIVE_VENDOR" : "PENDING_VENDOR", 100L)));
  }

  @Test
  void M4_1_供应商未开通建单被拒31003() {
    activeVendor(false);
    BizException ex = catchBiz(() -> service.create(cmd()));
    assertThat(ex.getErrorCode()).isEqualTo(ProcurementErrorCodes.VENDOR_NOT_ACTIVE);
  }

  @Test
  void M4_2_法人实体不匹配被拒31004() {
    when(vendorPort.findSnapshot(tenant, "SUP-1"))
        .thenReturn(
            Optional.of(new VendorQueryPort.VendorSnapshot("SUP-1", "ACTIVE_VENDOR", 999L)));
    BizException ex = catchBiz(() -> service.create(cmd()));
    assertThat(ex.getErrorCode()).isEqualTo(ProcurementErrorCodes.LEGAL_ENTITY_MISMATCH);
  }

  @Test
  void M4_5_金额不一致被拒31402() {
    activeVendor(true);
    CreatePurchaseOrderCommand bad =
        new CreatePurchaseOrderCommand(
            tenant,
            1L,
            "SUP-1",
            100L,
            new BigDecimal("999.0000"),
            null,
            null,
            null,
            "TAX-V2",
            null,
            cmd().lines());
    BizException ex = catchBiz(() -> service.create(bad));
    assertThat(ex).isNotNull();
    assertThat(ex.getErrorCode()).isEqualTo(ProcurementErrorCodes.PO_AMOUNT_MISMATCH);
  }

  @Test
  void M4_6_终态CLOSED回写被拒31403() {
    PurchaseOrder closed = new PurchaseOrder();
    closed.setStatus(PoStatus.CLOSED.name());
    closed.setTenantId(tenant);
    when(poRepo.findByNo(tenant, "PO-2026-000001")).thenReturn(Optional.of(closed));
    BizException ex = catchBiz(() -> service.transition(tenant, "PO-2026-000001", "SEND"));
    assertThat(ex.getErrorCode()).isEqualTo(ProcurementErrorCodes.TERMINAL_WRITE_BACK);
  }

  @Test
  void 列表分页按租户锚定并收窄越界参数() {
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNo("PO-2026-000001");
    PageResult<PurchaseOrder> page = PageResult.of(List.of(po), 1L, 1, 20);
    when(poRepo.findPage(tenant, "APPROVED", 1, 20)).thenReturn(page);

    PageResult<PurchaseOrder> got = service.list(tenant, "APPROVED", 1, 20);

    assertThat(got).isSameAs(page);
    verify(poRepo).findPage(tenant, "APPROVED", 1, 20);
    // 越界参数被收窄：page<1 → 1，size 上限 200
    service.list(tenant, null, -5, 9999);
    verify(poRepo).findPage(tenant, null, 1, 200);
  }

  @Test
  void M4_10_缺快照send被拒31405() {
    PurchaseOrder approved = new PurchaseOrder();
    approved.setStatus(PoStatus.APPROVED.name());
    approved.setTenantId(tenant);
    approved.setPoNo("PO-2026-000001");
    when(poRepo.findByNo(tenant, "PO-2026-000001")).thenReturn(Optional.of(approved));
    BizException ex = catchBiz(() -> service.send(tenant, "PO-2026-000001"));
    assertThat(ex.getErrorCode()).isEqualTo(ProcurementErrorCodes.PRICE_SNAPSHOT_MISSING);
  }

  private static BizException catchBiz(Runnable r) {
    try {
      r.run();
      return null;
    } catch (BizException e) {
      return e;
    }
  }
}
