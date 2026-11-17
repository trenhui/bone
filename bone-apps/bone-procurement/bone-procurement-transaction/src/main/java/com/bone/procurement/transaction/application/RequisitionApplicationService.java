package com.bone.procurement.transaction.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.transaction.application.command.CreatePurchaseOrderCommand;
import com.bone.procurement.transaction.application.command.CreateRequisitionCommand;
import com.bone.procurement.transaction.domain.model.requisition.Requisition;
import com.bone.procurement.transaction.domain.model.requisition.RequisitionEvent;
import com.bone.procurement.transaction.domain.model.requisition.RequisitionStatus;
import com.bone.procurement.transaction.domain.repository.RequisitionRepository;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 请购单应用服务。
 *
 * <p>@NoDomainEvent：本期单库本地事务切片，状态迁移均属内部迁移/终态到达，不发 DomainEvent（E-5.4）。
 */
@Service
@NoDomainEvent
public class RequisitionApplicationService {

  private final RequisitionRepository repository;
  private final OrderNoGenerator noGenerator;
  private final PurchaseOrderApplicationService poService;

  public RequisitionApplicationService(
      RequisitionRepository repository,
      OrderNoGenerator noGenerator,
      PurchaseOrderApplicationService poService) {
    this.repository = repository;
    this.noGenerator = noGenerator;
    this.poService = poService;
  }

  @Transactional
  public Requisition create(CreateRequisitionCommand cmd) {
    Requisition r = new Requisition();
    r.setTenantId(cmd.tenantId());
    r.setPrNo(noGenerator.nextPoRequisitionNo(cmd.tenantId()));
    r.setOrgId(cmd.orgId());
    r.setRequesterId(cmd.requesterId());
    r.setStatus(RequisitionStatus.DRAFT.name());
    r.setTotalAmount(cmd.totalAmount().setScale(4, java.math.RoundingMode.HALF_UP));
    r.setCurrency(cmd.currency());
    r.setBudgetRef(cmd.budgetRef());
    r.setVersion(0);
    r.setCreatedAt(new Date());
    r.setUpdatedAt(new Date());
    r.setId(repository.save(r));
    return r;
  }

  @Transactional
  public Requisition submit(Long tenantId, String prNo) {
    Requisition r = load(tenantId, prNo);
    r.setStatus(RequisitionStatus.transition(status(r), RequisitionEvent.SUBMIT).name());
    r.setUpdatedAt(new Date());
    repository.save(r);
    return r;
  }

  @Transactional
  public Requisition approve(Long tenantId, String prNo) {
    Requisition r = load(tenantId, prNo);
    r.setStatus(RequisitionStatus.transition(status(r), RequisitionEvent.APPROVE).name());
    r.setUpdatedAt(new Date());
    repository.save(r);
    return r;
  }

  @Transactional
  public Requisition cancel(Long tenantId, String prNo) {
    Requisition r = load(tenantId, prNo);
    r.setStatus(RequisitionStatus.transition(status(r), RequisitionEvent.CANCEL).name());
    r.setUpdatedAt(new Date());
    repository.save(r);
    return r;
  }

  /** 转采购订单：仅 APPROVED 可转；先在领域层校验状态（失败不改状态），再委托 PO 建单。 PO 建单失败时事务回滚，PR 保持 APPROVED。 */
  @Transactional
  public Requisition convert(Long tenantId, String prNo, String vendorPartyId, Long legalEntityId) {
    Requisition r = load(tenantId, prNo);
    RequisitionStatus next = RequisitionStatus.transition(status(r), RequisitionEvent.CONVERT);
    poService.create(
        new CreatePurchaseOrderCommand(
            tenantId,
            r.getOrgId(),
            vendorPartyId,
            legalEntityId,
            r.getTotalAmount(),
            null,
            null,
            null,
            null,
            r.getBudgetRef(),
            List.of()));
    r.setStatus(next.name());
    r.setUpdatedAt(new Date());
    repository.save(r);
    return r;
  }

  private Requisition load(Long tenantId, String prNo) {
    return repository
        .findByNo(tenantId, prNo)
        .orElseThrow(() -> new IllegalArgumentException("请购单不存在：" + prNo));
  }

  private static RequisitionStatus status(Requisition r) {
    return RequisitionStatus.valueOf(r.getStatus());
  }
}
