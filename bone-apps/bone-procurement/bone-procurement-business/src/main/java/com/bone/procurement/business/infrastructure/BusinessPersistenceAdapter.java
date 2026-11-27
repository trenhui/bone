package com.bone.procurement.business.infrastructure;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.business.application.BusinessRepository;
import com.bone.procurement.business.domain.model.business.Award;
import com.bone.procurement.business.domain.model.business.Bid;
import com.bone.procurement.business.domain.model.business.BidStatus;
import com.bone.procurement.business.domain.model.business.Capa;
import com.bone.procurement.business.domain.model.business.CapaStatus;
import com.bone.procurement.business.domain.model.business.Quotation;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 四类聚合的 SDK 持久化适配器：application 只依赖领域端口，不直接持有 SDK Repository。 */
@Component
public class BusinessPersistenceAdapter implements BusinessRepository {

  private final BidSdkRepository bidRepository;
  private final QuotationSdkRepository quotationRepository;
  private final AwardSdkRepository awardRepository;
  private final CapaSdkRepository capaRepository;

  public BusinessPersistenceAdapter(
      BidSdkRepository bidRepository,
      QuotationSdkRepository quotationRepository,
      AwardSdkRepository awardRepository,
      CapaSdkRepository capaRepository) {
    this.bidRepository = Objects.requireNonNull(bidRepository, "bidRepository");
    this.quotationRepository = Objects.requireNonNull(quotationRepository, "quotationRepository");
    this.awardRepository = Objects.requireNonNull(awardRepository, "awardRepository");
    this.capaRepository = Objects.requireNonNull(capaRepository, "capaRepository");
  }

  @Override
  public Bid saveBid(Bid bid) {
    requireTenant(bid.tenantId());
    if (bid.sourceRef() != null && !bid.sourceRef().isBlank()) {
      Optional<Bid> idempotent = findBidBySourceRef(bid.tenantId(), bid.sourceRef());
      if (idempotent.isPresent()) {
        return idempotent.get();
      }
    }
    Optional<BidEntity> sameSupplier =
        bidEntities(bid.tenantId()).stream()
            .filter(e -> bid.rfxNo().equals(e.getRfxNo()))
            .filter(e -> bid.supplierId().equals(e.getSupplierId()))
            .filter(e -> !bid.bidId().equals(e.getBidId()))
            .findFirst();
    if (sameSupplier.isPresent()) {
      throw new IllegalArgumentException("同一 RFx 下供应商重复投标");
    }
    Optional<BidEntity> existing = findBidEntity(bid.tenantId(), bid.bidId());
    if (existing.isPresent()) {
      Bid current = toBid(existing.get());
      if (current.status() == BidStatus.WITHDRAWN
          || current.status() == BidStatus.REJECTED
          || current.status() == BidStatus.SUBMITTED) {
        throw new IllegalStateException("投标终态不可回写");
      }
      if (!current.rfxNo().equals(bid.rfxNo())
          || !current.supplierId().equals(bid.supplierId())
          || !current.dueAt().equals(bid.dueAt())
          || !java.util.Objects.equals(current.sourceRef(), bid.sourceRef())) {
        throw new IllegalArgumentException("投标业务键不可变");
      }
    }
    BidEntity entity = existing.orElseGet(BidEntity::new);
    entity.setTenantId(bid.tenantId());
    entity.setBidId(bid.bidId());
    entity.setRfxNo(bid.rfxNo());
    entity.setSupplierId(bid.supplierId());
    entity.setSourceRef(bid.sourceRef());
    entity.setDueAt(bid.dueAt());
    entity.setStatus(bid.status().name());
    if (entity.getVersion() == null) {
      entity.setVersion(0);
    }
    stamp(entity);
    bidRepository.save(entity);
    return bid;
  }

  @Override
  public Optional<Bid> findBid(Long tenantId, String rfxNo, String supplierId) {
    requireTenant(tenantId);
    return bidEntities(tenantId).stream()
        .filter(e -> rfxNo.equals(e.getRfxNo()) && supplierId.equals(e.getSupplierId()))
        .findFirst()
        .map(BusinessPersistenceAdapter::toBid);
  }

  @Override
  public Optional<Bid> findBidById(Long tenantId, String bidId) {
    requireTenant(tenantId);
    return findBidEntity(tenantId, bidId).map(BusinessPersistenceAdapter::toBid);
  }

  @Override
  public Optional<Bid> findBidBySourceRef(Long tenantId, String sourceRef) {
    requireTenant(tenantId);
    if (sourceRef == null || sourceRef.isBlank()) {
      return Optional.empty();
    }
    return bidEntities(tenantId).stream()
        .filter(e -> sourceRef.equals(e.getSourceRef()))
        .findFirst()
        .map(BusinessPersistenceAdapter::toBid);
  }

  @Override
  public List<Bid> findBids(Long tenantId, String rfxNo) {
    requireTenant(tenantId);
    return bidEntities(tenantId).stream()
        .filter(e -> rfxNo.equals(e.getRfxNo()))
        .map(BusinessPersistenceAdapter::toBid)
        .toList();
  }

  @Override
  public Quotation saveQuotation(Quotation quotation) {
    requireTenant(quotation.tenantId());
    List<QuotationEntity> existing =
        all(quotationRepository, QuotationEntity.class).stream()
            .filter(e -> quotation.tenantId().equals(e.getTenantId()))
            .filter(e -> quotation.quoteId().equals(e.getQuoteId()))
            .toList();
    if (!existing.isEmpty() && existing.size() == 1) {
      QuotationEntity entity = existing.get(0);
      quotationToEntity(quotation, entity);
      stamp(entity);
      quotationRepository.save(entity);
      return quotation;
    }
    QuotationEntity entity = new QuotationEntity();
    entity.setVersion(0);
    quotationToEntity(quotation, entity);
    stamp(entity);
    quotationRepository.save(entity);
    return quotation;
  }

  @Override
  public List<Quotation> findQuotations(Long tenantId, String rfxNo) {
    requireTenant(tenantId);
    return all(quotationRepository, QuotationEntity.class).stream()
        .filter(e -> tenantId.equals(e.getTenantId()) && rfxNo.equals(e.getRfxNo()))
        .map(BusinessPersistenceAdapter::toQuotation)
        .toList();
  }

  @Override
  public Award saveAward(Award award) {
    requireTenant(award.tenantId());
    if (findAward(award.tenantId(), award.rfxNo()).isPresent()) {
      award.assertNotRewritable();
    }
    AwardEntity entity = new AwardEntity();
    entity.setVersion(0);
    entity.setTenantId(award.tenantId());
    entity.setAwardId(award.rfxNo() + ":" + award.supplierId());
    entity.setRfxNo(award.rfxNo());
    entity.setSupplierId(award.supplierId());
    entity.setConfirmedBy(award.confirmedBy());
    entity.setConfirmedAt(award.confirmedAt());
    entity.setReason(award.reason());
    stamp(entity);
    awardRepository.save(entity);
    return award;
  }

  @Override
  public Optional<Award> findAward(Long tenantId, String rfxNo) {
    requireTenant(tenantId);
    return all(awardRepository, AwardEntity.class).stream()
        .filter(e -> tenantId.equals(e.getTenantId()) && rfxNo.equals(e.getRfxNo()))
        .findFirst()
        .map(BusinessPersistenceAdapter::toAward);
  }

  @Override
  public Capa saveCapa(Capa capa) {
    requireTenant(capa.tenantId());
    if (findCapa(capa.tenantId(), capa.capaNo()).isPresent()) {
      throw new IllegalArgumentException("同一租户 CAPA 编号重复");
    }
    CapaEntity entity = new CapaEntity();
    entity.setVersion(0);
    capaToEntity(capa, entity);
    stamp(entity);
    capaRepository.save(entity);
    return capa;
  }

  @Override
  public Optional<Capa> findCapa(Long tenantId, String capaNo) {
    requireTenant(tenantId);
    return all(capaRepository, CapaEntity.class).stream()
        .filter(e -> tenantId.equals(e.getTenantId()) && capaNo.equals(e.getCapaNo()))
        .findFirst()
        .map(BusinessPersistenceAdapter::toCapa);
  }

  @Override
  public Capa updateCapa(Capa capa) {
    requireTenant(capa.tenantId());
    CapaEntity entity =
        all(capaRepository, CapaEntity.class).stream()
            .filter(e -> capa.tenantId().equals(e.getTenantId()))
            .filter(e -> capa.capaNo().equals(e.getCapaNo()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("CAPA 不存在"));
    if (CapaStatus.CLOSED.name().equals(entity.getStatus())) {
      throw new IllegalStateException("CAPA 已关闭，终态不可回写");
    }
    capaToEntity(capa, entity);
    stamp(entity);
    capaRepository.save(entity);
    return capa;
  }

  private List<BidEntity> bidEntities(Long tenantId) {
    return all(bidRepository, BidEntity.class).stream()
        .filter(e -> tenantId.equals(e.getTenantId()))
        .toList();
  }

  private Optional<BidEntity> findBidEntity(Long tenantId, String bidId) {
    return bidEntities(tenantId).stream().filter(e -> bidId.equals(e.getBidId())).findFirst();
  }

  private static <T extends com.bone.core.domain.entity.Entity<Long>> List<T> all(
      Repository<T, Long> repository, Class<T> type) {
    return repository.findByCriteria(Criteria.<T>create().entityClass(type));
  }

  private static void stamp(com.bone.core.domain.entity.AbstractEntity<Long> entity) {
    Date now = new Date();
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(now);
    }
    entity.setUpdatedAt(now);
  }

  private static void quotationToEntity(Quotation quotation, QuotationEntity entity) {
    entity.setTenantId(quotation.tenantId());
    entity.setQuoteId(quotation.quoteId());
    entity.setRfxNo(quotation.rfxNo());
    entity.setSupplierId(quotation.supplierId());
    entity.setAmount(quotation.amount());
    entity.setCurrency(quotation.currency());
    entity.setValidUntil(quotation.validUntil());
    entity.setAiScore(quotation.aiScore());
    entity.setAiRecommendation(quotation.aiRecommendation());
  }

  private static void capaToEntity(Capa capa, CapaEntity entity) {
    entity.setTenantId(capa.tenantId());
    entity.setCapaNo(capa.capaNo());
    entity.setSupplierId(capa.supplierId());
    entity.setIssueDescription(capa.issue());
    entity.setOwnerId(capa.owner());
    entity.setDueAt(capa.dueAt());
    entity.setStatus(capa.status().name());
    entity.setFollowUpRequired(capa.followUpRequired());
    entity.setClosedBy(capa.closedBy());
    entity.setClosedAt(capa.closedAt());
    entity.setEvidenceRef(capa.evidenceRef());
  }

  private static Bid toBid(BidEntity entity) {
    return Bid.restore(
        entity.getTenantId(),
        entity.getBidId(),
        entity.getRfxNo(),
        entity.getSupplierId(),
        entity.getSourceRef(),
        entity.getDueAt(),
        BidStatus.valueOf(entity.getStatus()));
  }

  private static Quotation toQuotation(QuotationEntity entity) {
    return new Quotation(
        entity.getTenantId(),
        entity.getQuoteId(),
        entity.getRfxNo(),
        entity.getSupplierId(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getValidUntil(),
        entity.getAiScore(),
        entity.getAiRecommendation());
  }

  private static Award toAward(AwardEntity entity) {
    return Award.decide(
        entity.getTenantId(),
        entity.getRfxNo(),
        entity.getSupplierId(),
        entity.getConfirmedBy(),
        entity.getReason(),
        entity.getConfirmedAt());
  }

  private static Capa toCapa(CapaEntity entity) {
    return Capa.restore(
        entity.getTenantId(),
        entity.getCapaNo(),
        entity.getSupplierId(),
        entity.getIssueDescription(),
        entity.getOwnerId(),
        entity.getDueAt(),
        CapaStatus.valueOf(entity.getStatus()),
        Boolean.TRUE.equals(entity.getFollowUpRequired()),
        entity.getClosedBy(),
        entity.getClosedAt(),
        entity.getEvidenceRef());
  }

  private static void requireTenant(Long tenantId) {
    if (tenantId == null || tenantId <= 0) {
      throw new IllegalArgumentException("缺失登录态租户，fail-closed");
    }
  }
}
