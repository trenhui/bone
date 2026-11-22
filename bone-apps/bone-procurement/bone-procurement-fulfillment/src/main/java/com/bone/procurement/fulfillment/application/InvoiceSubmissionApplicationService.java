package com.bone.procurement.fulfillment.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.fulfillment.application.command.InvoiceCommand;
import com.bone.procurement.fulfillment.domain.FulfillmentErrorCodes;
import com.bone.procurement.fulfillment.domain.FulfillmentErrors;
import com.bone.procurement.fulfillment.domain.model.invoice.InvoiceStatus;
import com.bone.procurement.fulfillment.domain.model.invoice.InvoiceSubmission;
import com.bone.procurement.fulfillment.domain.port.FinanceAclPort;
import com.bone.procurement.fulfillment.domain.port.PoQueryPort;
import com.bone.procurement.fulfillment.domain.port.ToleranceConfigPort;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.InvoiceSubmissionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自助开票应用服务：去重 + 未开票余额 + TDS 校验 + 只提交不裁决。
 *
 * <p>997 语法回执只推进到 MATCH_PENDING；Finance 匹配结果经 applyFinanceResult 回写，采购域无放行入口（PA-04）。
 */
@Service
@NoDomainEvent
public class InvoiceSubmissionApplicationService {

  private final InvoiceSubmissionRepository repository;
  private final PoQueryPort poQuery;
  private final VendorIdentityPort identity;
  private final ToleranceConfigPort tolerance;
  private final FinanceAclPort financeAcl;

  public InvoiceSubmissionApplicationService(
      InvoiceSubmissionRepository repository,
      PoQueryPort poQuery,
      VendorIdentityPort identity,
      ToleranceConfigPort tolerance,
      FinanceAclPort financeAcl) {
    this.repository = repository;
    this.poQuery = poQuery;
    this.identity = identity;
    this.tolerance = tolerance;
    this.financeAcl = financeAcl;
  }

  @Transactional
  public InvoiceSubmission submit(InvoiceCommand cmd) {
    String vendor = requireIdentity();
    // INV-07 / M5-2：(tenant, invoice_no, vendor) 去重
    Optional<InvoiceSubmission> existing =
        repository.findByDedupKey(cmd.tenantId(), cmd.invoiceNo(), vendor);
    if (existing.isPresent()) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.INVOICE_DUPLICATE, cmd.invoiceNo());
    }

    // TDS 校验：Σ IT1 ± SAC，逐行舍入后汇总；超容差拒收并生成例外（由调用方/异常落台账）
    BigDecimal computed = sumLines(cmd);
    BigDecimal tol = tolerance.tolerance(cmd.tenantId(), vendor, cmd.category());
    BigDecimal diff = cmd.amount().subtract(computed).abs();
    if (diff.compareTo(tol) > 0) {
      throw FulfillmentErrors.of(
          FulfillmentErrorCodes.ASN_GR_MISMATCH,
          "TDS 差额=" + diff + " 超容差=" + tol + "（发票 " + cmd.invoiceNo() + "）");
    }

    // INV-08 / M5-5：金额 ≤ PO 未开票余额
    if (cmd.poNo() != null && !cmd.poNo().isBlank()) {
      PoQueryPort.PoSnapshot po =
          poQuery
              .findByNo(cmd.tenantId(), cmd.poNo())
              .orElseThrow(() -> FulfillmentErrors.of("PO_NOT_FOUND", cmd.poNo()));
      if (po.vendorPartyId() == null || !po.vendorPartyId().equals(vendor)) {
        throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "非本供应商的 PO");
      }
      if (cmd.amount().compareTo(po.openAmount()) > 0) {
        throw FulfillmentErrors.of(
            FulfillmentErrorCodes.INVOICE_OVER_BALANCE,
            "发票金额=" + cmd.amount() + ", 未开票余额=" + po.openAmount());
      }
    }

    InvoiceSubmission inv = new InvoiceSubmission();
    inv.setTenantId(cmd.tenantId());
    inv.setInvoiceNo(cmd.invoiceNo());
    inv.setVendorPartyId(vendor);
    inv.setPoNo(cmd.poNo());
    inv.setAmount(cmd.amount());
    inv.setTax(cmd.tax() == null ? BigDecimal.ZERO : cmd.tax());
    inv.setCurrency(cmd.currency() == null ? "CNY" : cmd.currency());
    inv.setLinesJson(toJson(cmd));
    inv.setStatus(InvoiceStatus.DRAFT.name());
    inv.setVersion(0);
    Date now = new Date();
    inv.setCreatedAt(now);
    inv.setUpdatedAt(now);
    Long id = repository.save(inv);
    inv.setId(id);
    return inv;
  }

  /** 997 语法回执：只 SUBMITTED → MATCH_PENDING，绝不置 MATCHED（M5-7）。 */
  @Transactional
  public InvoiceSubmission applySyntaxAck(Long tenantId, String invoiceNo) {
    String vendor = requireIdentity();
    InvoiceSubmission inv = requireOwned(tenantId, invoiceNo, vendor);
    inv.setStatus(
        InvoiceStatus.transition(
                InvoiceStatus.valueOf(inv.getStatus()), InvoiceStatus.Event.finance_ack)
            .name());
    repository.save(inv);
    return inv;
  }

  /**
   * Finance 匹配结果回写（只回写不裁决；PA-04）：MATCH_PENDING → MATCHED/BLOCKED。 本方法仅供 Finance 事件入口调用，采购侧无放行接口。
   */
  @Transactional
  public InvoiceSubmission applyFinanceResult(
      Long tenantId,
      String invoiceNo,
      String vendorPartyId,
      String matchStatus,
      String financeEventId) {
    InvoiceSubmission inv =
        repository
            .findByDedupKey(tenantId, invoiceNo, vendorPartyId)
            .orElseThrow(() -> FulfillmentErrors.of("INVOICE_NOT_FOUND", invoiceNo));
    if (!financeAcl.isAvailable()) {
      // 上游不可用：不推断结果，保持状态（M5-11）
      return inv;
    }
    if ("MATCHED".equals(matchStatus)) {
      inv.setStatus(InvoiceStatus.MATCHED.name());
    } else if ("EXCEPTION".equals(matchStatus) || "REVIEW".equals(matchStatus)) {
      inv.setStatus(InvoiceStatus.BLOCKED.name());
    }
    inv.setFinanceEventId(financeEventId);
    repository.save(inv);
    return inv;
  }

  /** 采购域越权放行必须被拒绝（M5-6）。 */
  public void rejectProcurementRelease() {
    throw FulfillmentErrors.of(FulfillmentErrorCodes.PROCUREMENT_RELEASE_FORBIDDEN, "采购域无发票放行权限");
  }

  private InvoiceSubmission requireOwned(Long tenantId, String invoiceNo, String vendor) {
    InvoiceSubmission inv =
        repository
            .findByDedupKey(tenantId, invoiceNo, vendor)
            .orElseThrow(
                () ->
                    FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "非本供应商的发票"));
    return inv;
  }

  private String requireIdentity() {
    try {
      return identity.requireVendorPartyId();
    } catch (IllegalStateException e) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "无供应商身份");
    }
  }

  private BigDecimal sumLines(InvoiceCommand cmd) {
    BigDecimal total = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    if (cmd.lines() != null) {
      for (InvoiceCommand.InvoiceLine l : cmd.lines()) {
        BigDecimal amount =
            l.amount() == null
                ? l.qty().multiply(l.price()).setScale(4, RoundingMode.HALF_UP)
                : l.amount().setScale(4, RoundingMode.HALF_UP);
        total = total.add(amount);
      }
    }
    return total;
  }

  private String toJson(InvoiceCommand cmd) {
    // 不回传 PII；仅保存行金额结构
    return "[TDS," + cmd.amount().toPlainString() + "," + cmd.currency() + "]";
  }
}
