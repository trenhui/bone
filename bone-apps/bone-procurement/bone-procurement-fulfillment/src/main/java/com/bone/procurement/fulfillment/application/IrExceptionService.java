package com.bone.procurement.fulfillment.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.model.PageResult;
import com.bone.procurement.fulfillment.domain.FulfillmentErrorCodes;
import com.bone.procurement.fulfillment.domain.FulfillmentErrors;
import com.bone.procurement.fulfillment.domain.model.exception.IrException;
import com.bone.procurement.fulfillment.domain.model.exception.IrExceptionStatus;
import com.bone.procurement.fulfillment.domain.port.FinanceAclPort;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.InvoiceSubmissionRepository;
import com.bone.procurement.fulfillment.domain.repository.IrExceptionRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** IR 例外服务：登记/补料 + Finance 事件回写（只回写不裁决，采购域无放行入口）。 */
@Service
@NoDomainEvent
public class IrExceptionService {

  private final IrExceptionRepository repository;
  private final InvoiceSubmissionRepository invoiceRepository;
  private final VendorIdentityPort identity;
  private final FinanceAclPort financeAcl;

  public IrExceptionService(
      IrExceptionRepository repository,
      InvoiceSubmissionRepository invoiceRepository,
      VendorIdentityPort identity,
      FinanceAclPort financeAcl) {
    this.repository = repository;
    this.invoiceRepository = invoiceRepository;
    this.identity = identity;
    this.financeAcl = financeAcl;
  }

  /** 生成或复用例外（INV-11 幂等）。采购域只生成，不裁决。 */
  @Transactional
  public IrException raise(
      Long tenantId,
      String exceptionNo,
      String invoiceNo,
      String exceptionCode,
      String matchStatus) {
    Optional<IrException> existing = repository.findByDedupKey(tenantId, invoiceNo, exceptionCode);
    if (existing.isPresent()) {
      return existing.get();
    }
    IrException ex = new IrException();
    ex.setTenantId(tenantId);
    ex.setExceptionNo(exceptionNo);
    ex.setInvoiceNo(invoiceNo);
    ex.setExceptionCode(exceptionCode);
    ex.setMatchStatus(matchStatus);
    ex.setStatus(IrExceptionStatus.OPEN.name());
    ex.setVersion(0);
    Long id = repository.save(ex);
    ex.setId(id);
    return ex;
  }

  /** 补料/申诉：OPEN → SUPPLEMENTED，无放行能力。 */
  @Transactional
  public IrException supplement(Long tenantId, String exceptionNo, String supplementRef) {
    String vendor = requireIdentity();
    IrException ex =
        repository
            .findByExceptionNo(tenantId, exceptionNo)
            .orElseThrow(() -> FulfillmentErrors.of("IR_EXCEPTION_NOT_FOUND", exceptionNo));
    // 越权：例外关联发票的归属供应商必须等于当前身份
    String vendorOfInvoice = findVendorOfInvoice(tenantId, ex.getInvoiceNo());
    if (vendorOfInvoice == null || !vendor.equals(vendorOfInvoice)) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "非本供应商的例外");
    }
    if (!IrExceptionStatus.OPEN.name().equals(ex.getStatus())) {
      throw FulfillmentErrors.of(
          FulfillmentErrorCodes.INVALID_TRANSITION, "例外状态=" + ex.getStatus());
    }
    ex.setStatus(
        IrExceptionStatus.transition(
                IrExceptionStatus.valueOf(ex.getStatus()), IrExceptionStatus.Event.supplement)
            .name());
    ex.setSupplementRef(supplementRef);
    repository.save(ex);
    return ex;
  }

  /** Finance 事件回写 RESOLVED/REJECTED（唯一来源）。上游不可用时保持待态、不关闭（M5-11 / INV-10）。 */
  @Transactional
  public IrException applyFinanceDecision(
      Long tenantId, String exceptionNo, boolean resolve, String financeEventId) {
    IrException ex =
        repository
            .findByExceptionNo(tenantId, exceptionNo)
            .orElseThrow(() -> FulfillmentErrors.of("IR_EXCEPTION_NOT_FOUND", exceptionNo));
    if (!financeAcl.isAvailable()) {
      return ex; // 不推断、不放行
    }
    ex.setStatus(resolve ? IrExceptionStatus.RESOLVED.name() : IrExceptionStatus.REJECTED.name());
    ex.setFinanceEventId(financeEventId);
    repository.save(ex);
    return ex;
  }

  /** 采购域越权放行必须被拒绝（M5-6 / 31505）。 */
  public void rejectProcurementRelease() {
    throw FulfillmentErrors.of(FulfillmentErrorCodes.PROCUREMENT_RELEASE_FORBIDDEN, "采购域无 IR 放行权限");
  }

  @Transactional(readOnly = true)
  public PageResult<IrException> list(Long tenantId, int page, int size) {
    int safePage = Math.max(page, 1);
    int safeSize = Math.min(Math.max(size, 1), 200);
    return repository.findPage(tenantId, safePage, safeSize);
  }

  private String findVendorOfInvoice(Long tenantId, String invoiceNo) {
    var page = invoiceRepository.findPageByTenant(tenantId, 1, 200);
    if (page == null || page.getRecords() == null) return null;
    return page.getRecords().stream()
        .filter(i -> invoiceNo.equals(i.getInvoiceNo()))
        .map(i -> i.getVendorPartyId())
        .findFirst()
        .orElse(null);
  }

  private String requireIdentity() {
    try {
      return identity.requireVendorPartyId();
    } catch (IllegalStateException e) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "无供应商身份");
    }
  }
}
