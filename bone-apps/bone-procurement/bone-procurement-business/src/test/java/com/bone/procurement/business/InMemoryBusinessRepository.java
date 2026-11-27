package com.bone.procurement.business;

import com.bone.procurement.business.application.BusinessRepository;
import com.bone.procurement.business.domain.model.business.Award;
import com.bone.procurement.business.domain.model.business.Bid;
import com.bone.procurement.business.domain.model.business.Capa;
import com.bone.procurement.business.domain.model.business.Quotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 领域测试用内存仓储；只实现领域端口语义，不代表生产持久化。 */
public class InMemoryBusinessRepository implements BusinessRepository {

  private final Map<String, Bid> bids = new LinkedHashMap<>();
  private final Map<String, Quotation> quotations = new LinkedHashMap<>();
  private final Map<String, Award> awards = new LinkedHashMap<>();
  private final Map<String, Capa> capas = new LinkedHashMap<>();

  @Override
  public Bid saveBid(Bid bid) {
    requireTenant(bid.tenantId());
    for (Bid existing : bids.values()) {
      if (!bid.bidId().equals(existing.bidId())
          && sameTenant(bid.tenantId(), existing.tenantId())
          && bid.rfxNo().equals(existing.rfxNo())
          && bid.supplierId().equals(existing.supplierId())) {
        throw new IllegalArgumentException("同一 RFx 下供应商重复投标");
      }
    }
    bids.put(key(bid.tenantId(), bid.bidId()), bid);
    return bid;
  }

  @Override
  public Optional<Bid> findBid(Long tenantId, String rfxNo, String supplierId) {
    requireTenant(tenantId);
    return bids.values().stream()
        .filter(b -> sameTenant(tenantId, b.tenantId()))
        .filter(b -> b.rfxNo().equals(rfxNo) && b.supplierId().equals(supplierId))
        .findFirst();
  }

  @Override
  public Optional<Bid> findBidById(Long tenantId, String bidId) {
    requireTenant(tenantId);
    return Optional.ofNullable(bids.get(key(tenantId, bidId)))
        .filter(b -> sameTenant(tenantId, b.tenantId()))
        .map(InMemoryBusinessRepository::copy);
  }

  @Override
  public Optional<Bid> findBidBySourceRef(Long tenantId, String sourceRef) {
    requireTenant(tenantId);
    if (sourceRef == null || sourceRef.isBlank()) {
      return Optional.empty();
    }
    return bids.values().stream()
        .filter(b -> sameTenant(tenantId, b.tenantId()) && sourceRef.equals(b.sourceRef()))
        .findFirst();
  }

  @Override
  public List<Bid> findBids(Long tenantId, String rfxNo) {
    requireTenant(tenantId);
    return bids.values().stream()
        .filter(b -> sameTenant(tenantId, b.tenantId()) && b.rfxNo().equals(rfxNo))
        .toList();
  }

  @Override
  public Quotation saveQuotation(Quotation quotation) {
    requireTenant(quotation.tenantId());
    quotations.put(key(quotation.tenantId(), quotation.quoteId()), quotation);
    return quotation;
  }

  @Override
  public List<Quotation> findQuotations(Long tenantId, String rfxNo) {
    requireTenant(tenantId);
    return quotations.values().stream()
        .filter(q -> sameTenant(tenantId, q.tenantId()) && q.rfxNo().equals(rfxNo))
        .toList();
  }

  @Override
  public Award saveAward(Award award) {
    requireTenant(award.tenantId());
    String k = key(award.tenantId(), award.rfxNo());
    if (awards.containsKey(k)) {
      throw new IllegalStateException("授标为终态，禁止重复或回写");
    }
    awards.put(k, award);
    return award;
  }

  @Override
  public Optional<Award> findAward(Long tenantId, String rfxNo) {
    requireTenant(tenantId);
    return Optional.ofNullable(awards.get(key(tenantId, rfxNo)))
        .filter(a -> sameTenant(tenantId, a.tenantId()));
  }

  @Override
  public Capa saveCapa(Capa capa) {
    requireTenant(capa.tenantId());
    String k = key(capa.tenantId(), capa.capaNo());
    if (capas.containsKey(k)) {
      throw new IllegalArgumentException("同一租户 CAPA 编号重复");
    }
    capas.put(k, copy(capa));
    return capa;
  }

  @Override
  public Optional<Capa> findCapa(Long tenantId, String capaNo) {
    requireTenant(tenantId);
    return Optional.ofNullable(capas.get(key(tenantId, capaNo)))
        .filter(c -> sameTenant(tenantId, c.tenantId()))
        .map(InMemoryBusinessRepository::copy);
  }

  @Override
  public Capa updateCapa(Capa capa) {
    requireTenant(capa.tenantId());
    String k = key(capa.tenantId(), capa.capaNo());
    Capa existing = capas.get(k);
    if (existing == null) {
      throw new IllegalArgumentException("CAPA 不存在");
    }
    existing.assertWritable();
    capas.put(k, copy(capa));
    return capa;
  }

  private static Bid copy(Bid bid) {
    return Bid.restore(
        bid.tenantId(),
        bid.bidId(),
        bid.rfxNo(),
        bid.supplierId(),
        bid.sourceRef(),
        bid.dueAt(),
        bid.status());
  }

  private static Capa copy(Capa capa) {
    return Capa.restore(
        capa.tenantId(),
        capa.capaNo(),
        capa.supplierId(),
        capa.issue(),
        capa.owner(),
        capa.dueAt(),
        capa.status(),
        capa.followUpRequired(),
        capa.closedBy(),
        capa.closedAt(),
        capa.evidenceRef());
  }

  public int size() {
    return bids.size() + quotations.size() + awards.size() + capas.size();
  }

  private static boolean sameTenant(Long left, Long right) {
    return left != null && left.equals(right);
  }

  private static void requireTenant(Long tenantId) {
    if (tenantId == null || tenantId <= 0) {
      throw new IllegalArgumentException("缺失登录态租户，fail-closed");
    }
  }

  private static String key(Object first, Object second) {
    return first + ":" + second;
  }
}
