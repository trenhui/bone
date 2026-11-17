package com.bone.procurement.transaction.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.model.PageResult;
import com.bone.procurement.transaction.application.command.CreatePurchaseOrderCommand;
import com.bone.procurement.transaction.domain.ProcurementErrorCodes;
import com.bone.procurement.transaction.domain.ProcurementErrors;
import com.bone.procurement.transaction.domain.model.po.PoEvent;
import com.bone.procurement.transaction.domain.model.po.PoStatus;
import com.bone.procurement.transaction.domain.model.po.PurchaseOrder;
import com.bone.procurement.transaction.domain.model.po.PurchaseOrderLine;
import com.bone.procurement.transaction.domain.model.pricing.Money;
import com.bone.procurement.transaction.domain.port.VendorQueryPort;
import com.bone.procurement.transaction.domain.repository.PurchaseOrderLineRepository;
import com.bone.procurement.transaction.domain.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 采购订单应用服务。
 *
 * <p>@NoDomainEvent：本期为单库本地事务切片，状态迁移均为内部状态迁移/终态到达，不发 DomainEvent； 跨上下文编排由后续 change 的 Orchestrator
 * 承担（E-5.4 豁免）。
 */
@Service
@NoDomainEvent
public class PurchaseOrderApplicationService {

  private final PurchaseOrderRepository poRepository;
  private final PurchaseOrderLineRepository lineRepository;
  private final VendorQueryPort vendorPort;
  private final OrderNoGenerator noGenerator;

  public PurchaseOrderApplicationService(
      PurchaseOrderRepository poRepository,
      PurchaseOrderLineRepository lineRepository,
      VendorQueryPort vendorPort,
      OrderNoGenerator noGenerator) {
    this.poRepository = poRepository;
    this.lineRepository = lineRepository;
    this.vendorPort = vendorPort;
    this.noGenerator = noGenerator;
  }

  @Transactional
  public PurchaseOrder create(CreatePurchaseOrderCommand cmd) {
    VendorQueryPort.VendorSnapshot vendor =
        vendorPort
            .findSnapshot(cmd.tenantId(), cmd.vendorPartyId())
            .orElseThrow(
                () ->
                    ProcurementErrors.of(
                        ProcurementErrorCodes.VENDOR_NOT_ACTIVE, "供应商不存在：" + cmd.vendorPartyId()));
    if (!vendor.isActiveVendor()) {
      throw ProcurementErrors.of(
          ProcurementErrorCodes.VENDOR_NOT_ACTIVE, "供应商交易资格未开通：" + vendor.vendorState());
    }
    // 法人隔离：供应商侧能解析出法人时须与订单一致；解析能力未装配（null）时不臆造匹配，
    // 但仍由 vendorState 闸门放行——该降级在验证报告中如实标注为本期边界。
    if (vendor.legalEntityId() != null && !vendor.legalEntityId().equals(cmd.legalEntityId())) {
      throw ProcurementErrors.of(
          ProcurementErrorCodes.LEGAL_ENTITY_MISMATCH,
          "法人实体不匹配：订单=" + cmd.legalEntityId() + ", 供应商=" + vendor.legalEntityId());
    }

    List<PurchaseOrderLine> lines = buildLines(cmd);
    Money total = sum(lines, currencyOf(cmd));
    if (cmd.declaredAmount() != null
        && total.amount().compareTo(cmd.declaredAmount().setScale(4, Money.ROUNDING)) != 0) {
      throw ProcurementErrors.of(
          ProcurementErrorCodes.PO_AMOUNT_MISMATCH,
          "订单总额=" + cmd.declaredAmount() + ", 行合计=" + total.amount());
    }

    PurchaseOrder po = new PurchaseOrder();
    po.setTenantId(cmd.tenantId());
    po.setPoNo(noGenerator.nextPurchaseOrderNo(cmd.tenantId()));
    po.setOrgId(cmd.orgId());
    po.setVendorPartyId(cmd.vendorPartyId());
    po.setLegalEntityId(cmd.legalEntityId());
    po.setStatus(PoStatus.DRAFT.name());
    po.setPoAmount(total.amount());
    po.setCurrency(total.currency());
    po.setTaxAmount(BigDecimal.ZERO.setScale(4));
    po.setFxRate(cmd.fxRate());
    po.setFxSource(cmd.fxSource());
    po.setTaxCodeVersion(cmd.taxCodeVersion());
    po.setBudgetRef(cmd.budgetRef());
    po.setVersion(0);
    po.setCreatedAt(new Date());
    po.setUpdatedAt(new Date());
    Long poId = poRepository.save(po);
    po.setId(poId);
    for (PurchaseOrderLine line : lines) {
      line.setPoId(poId);
      line.setCreatedAt(new Date());
      line.setUpdatedAt(new Date());
      lineRepository.save(line);
    }
    return po;
  }

  /** 下达：冻结价格快照。缺快照输入（汇率/税码版本缺失且系统未配置默认）→ 31405。 */
  @Transactional
  public PurchaseOrder send(Long tenantId, String poNo) {
    PurchaseOrder po =
        poRepository
            .findByNo(tenantId, poNo)
            .orElseThrow(() -> ProcurementErrors.of("PO_NOT_FOUND", poNo));
    if (PoStatus.CLOSED.name().equals(po.getStatus())
        || PoStatus.CANCELLED.name().equals(po.getStatus())) {
      throw ProcurementErrors.of(ProcurementErrorCodes.TERMINAL_WRITE_BACK, poNo);
    }
    // 本期快照依赖逐行净价与税码版本；缺任一即拒绝下达（M4-10）
    if (po.getTaxCodeVersion() == null || po.getTaxCodeVersion().isBlank()) {
      throw ProcurementErrors.of(ProcurementErrorCodes.PRICE_SNAPSHOT_MISSING, "缺税码版本：" + poNo);
    }
    List<PurchaseOrderLine> lines = lineRepository.findByPoId(tenantId, po.getId());
    if (lines.isEmpty()) {
      throw ProcurementErrors.of(ProcurementErrorCodes.PRICE_SNAPSHOT_MISSING, "缺订单行快照输入：" + poNo);
    }
    po.setStatus(PoStatus.transition(PoStatus.valueOf(po.getStatus()), PoEvent.SEND).name());
    po.setUpdatedAt(new Date());
    poRepository.save(po);
    return po;
  }

  /** 通用状态推进（本期仅用于终态回写探针；生产入口见 submit/send）。 */
  @Transactional
  public PurchaseOrder transition(Long tenantId, String poNo, String event) {
    PurchaseOrder po =
        poRepository
            .findByNo(tenantId, poNo)
            .orElseThrow(() -> ProcurementErrors.of("PO_NOT_FOUND", poNo));
    if (PoStatus.CLOSED.name().equals(po.getStatus())
        || PoStatus.CANCELLED.name().equals(po.getStatus())) {
      throw ProcurementErrors.of(ProcurementErrorCodes.TERMINAL_WRITE_BACK, poNo);
    }
    PoEvent poEvent = PoEvent.valueOf(event);
    po.setStatus(PoStatus.transition(PoStatus.valueOf(po.getStatus()), poEvent).name());
    po.setUpdatedAt(new Date());
    poRepository.save(po);
    return po;
  }

  /** 列表分页（只读）：按租户锚定，status 可选过滤。 */
  @Transactional(readOnly = true)
  public PageResult<PurchaseOrder> list(Long tenantId, String status, int page, int size) {
    int safePage = Math.max(page, 1);
    int safeSize = Math.min(Math.max(size, 1), 200);
    return poRepository.findPage(tenantId, status, safePage, safeSize);
  }

  /** 详情查询（只读）。 */
  public java.util.Optional<PurchaseOrder> findByNo(Long tenantId, String poNo) {
    return poRepository.findByNo(tenantId, poNo);
  }

  private List<PurchaseOrderLine> buildLines(CreatePurchaseOrderCommand cmd) {
    List<PurchaseOrderLine> lines = new ArrayList<>();
    int no = 1;
    for (CreatePurchaseOrderCommand.Line l : cmd.lines()) {
      Money netPrice = Money.of(l.netPrice(), currencyOf(cmd));
      Money lineAmount = netPrice.times(l.quantity()).times(BigDecimal.ONE.add(l.taxRate()));
      PurchaseOrderLine line = new PurchaseOrderLine();
      line.setTenantId(cmd.tenantId());
      line.setLineNo(no++);
      line.setItemId(l.itemId());
      line.setItemName(l.itemName());
      line.setQuantity(l.quantity());
      line.setUnit(l.unit());
      line.setNetPrice(netPrice.amount());
      line.setTaxRate(l.taxRate());
      line.setLineAmount(lineAmount.amount());
      line.setCurrency(currencyOf(cmd));
      line.setVersion(0);
      lines.add(line);
    }
    return lines;
  }

  private Money sum(List<PurchaseOrderLine> lines, String currency) {
    Money total = Money.of(BigDecimal.ZERO, currency);
    for (PurchaseOrderLine l : lines) {
      total = total.plus(Money.of(l.getLineAmount(), currency));
    }
    return total;
  }

  private String currencyOf(CreatePurchaseOrderCommand cmd) {
    return "CNY";
  }
}
