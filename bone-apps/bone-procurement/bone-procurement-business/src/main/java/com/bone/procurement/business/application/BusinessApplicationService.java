package com.bone.procurement.business.application;

import com.bone.procurement.business.domain.gateway.BusinessIdentityPort;
import com.bone.procurement.business.domain.model.business.Award;
import com.bone.procurement.business.domain.model.business.Bid;
import com.bone.procurement.business.domain.model.business.Capa;
import com.bone.procurement.business.domain.model.business.Quotation;
import com.bone.procurement.business.domain.model.business.QuotationComparison;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 只读查询与具名人工操作；身份只来自登录态端口，租户不一致一律 fail-closed。 */
@Service
public class BusinessApplicationService {

  private final BusinessRepository repository;
  private final BusinessIdentityPort identity;

  public BusinessApplicationService(BusinessRepository repository, BusinessIdentityPort identity) {
    this.repository = repository;
    this.identity = identity;
  }

  public Bid submitBid(
      Long requestedTenantId,
      String bidId,
      String rfxNo,
      String supplierId,
      String sourceRef,
      Date dueAt,
      Date now) {
    Long tenantId = tenant(requestedTenantId);
    if (sourceRef != null && !sourceRef.isBlank()) {
      Optional<Bid> existing = repository.findBidBySourceRef(tenantId, sourceRef);
      if (existing.isPresent()) {
        return existing.get();
      }
    }
    Optional<Bid> existing = repository.findBidById(tenantId, bidId);
    if (existing.isPresent()) {
      Bid stored = existing.get();
      if (stored.status() != com.bone.procurement.business.domain.model.business.BidStatus.DRAFT) {
        throw new IllegalStateException("投标终态不可回写或从撤回态复活");
      }
      if (!stored.rfxNo().equals(rfxNo)
          || !stored.supplierId().equals(supplierId)
          || !stored.dueAt().equals(dueAt)
          || !java.util.Objects.equals(stored.sourceRef(), sourceRef)) {
        throw new IllegalArgumentException("投标业务键不可变");
      }
      return repository.saveBid(stored.submit(now));
    }
    Bid bid = Bid.draft(tenantId, bidId, rfxNo, supplierId, sourceRef, dueAt).submit(now);
    return repository.saveBid(bid);
  }

  public Quotation recordQuotation(
      Long requestedTenantId,
      String quoteId,
      String rfxNo,
      String supplierId,
      BigDecimal amount,
      String currency,
      Date validUntil,
      BigDecimal aiScore,
      String aiRecommendation) {
    return repository.saveQuotation(
        new Quotation(
            tenant(requestedTenantId),
            quoteId,
            rfxNo,
            supplierId,
            amount,
            currency,
            validUntil,
            aiScore,
            aiRecommendation));
  }

  public List<QuotationComparison> compareQuotations(
      Long requestedTenantId, String rfxNo, String budgetCurrency, Date now) {
    Long tenantId = tenant(requestedTenantId);
    return QuotationComparison.compare(
        repository.findQuotations(tenantId, rfxNo), budgetCurrency, now);
  }

  public Award award(
      Long requestedTenantId, String rfxNo, String supplierId, String reason, Date now) {
    Long tenantId = tenant(requestedTenantId);
    String operator = require().operator();
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("人工授标需要登录态操作人，禁止匿名终决");
    }
    return repository.saveAward(Award.decide(tenantId, rfxNo, supplierId, operator, reason, now));
  }

  public Optional<Award> findAward(Long requestedTenantId, String rfxNo) {
    return repository.findAward(tenant(requestedTenantId), rfxNo);
  }

  public Capa createCapa(
      Long requestedTenantId,
      String capaNo,
      String supplierId,
      String issue,
      String owner,
      Date dueAt) {
    return repository.saveCapa(
        Capa.open(tenant(requestedTenantId), capaNo, supplierId, issue, owner, dueAt));
  }

  public Capa markCapaOverdue(Long requestedTenantId, String capaNo, Date now) {
    Long tenantId = tenant(requestedTenantId);
    Capa capa =
        repository
            .findCapa(tenantId, capaNo)
            .orElseThrow(() -> new IllegalArgumentException("CAPA 不存在"));
    return repository.updateCapa(capa.markOverdue(now));
  }

  public Capa closeCapa(Long requestedTenantId, String capaNo, String evidence, Date now) {
    Long tenantId = tenant(requestedTenantId);
    Capa capa =
        repository
            .findCapa(tenantId, capaNo)
            .orElseThrow(() -> new IllegalArgumentException("CAPA 不存在"));
    String operator = require().operator();
    return repository.updateCapa(capa.close(operator, evidence, now));
  }

  private BusinessIdentityPort.Actor require() {
    BusinessIdentityPort.Actor actor = identity.current();
    if (actor == null || actor.tenantId() == null || actor.tenantId() <= 0) {
      throw new IllegalArgumentException("缺失登录态租户与操作人，fail-closed");
    }
    return actor;
  }

  private Long tenant(Long requestedTenantId) {
    Long tenantId = require().tenantId();
    if (requestedTenantId != null && !requestedTenantId.equals(tenantId)) {
      throw new IllegalArgumentException("请求租户与登录态不一致，禁止跨租户访问");
    }
    return tenantId;
  }
}
