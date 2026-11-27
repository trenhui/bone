package com.bone.procurement.business.infrastructure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 四个 SDK 仓储共享同一底层存储，模拟进程重启后重建 Adapter 仍读同一数据库。 */
final class BusinessSdkFixture {

  private final Map<Long, BidEntity> bids = new LinkedHashMap<>();
  private final Map<Long, QuotationEntity> quotations = new LinkedHashMap<>();
  private final Map<Long, AwardEntity> awards = new LinkedHashMap<>();
  private final Map<Long, CapaEntity> capas = new LinkedHashMap<>();
  private final AtomicLong sequence = new AtomicLong(1000);

  BusinessPersistenceAdapter newAdapter() {
    return new BusinessPersistenceAdapter(
        newBidRepo(), newQuotationRepo(), newAwardRepo(), newCapaRepo());
  }

  int storedAwards() {
    return awards.size();
  }

  java.util.Date createdAtOfCapa(String capaNo) {
    return capas.values().stream()
        .filter(entity -> capaNo.equals(entity.getCapaNo()))
        .findFirst()
        .map(com.bone.core.domain.entity.AbstractEntity::getCreatedAt)
        .orElse(null);
  }

  private BidSdkRepository newBidRepo() {
    return new BidRepo(bids, sequence);
  }

  private QuotationSdkRepository newQuotationRepo() {
    return new QuotationRepo(quotations, sequence);
  }

  private AwardSdkRepository newAwardRepo() {
    return new AwardRepo(awards, sequence);
  }

  private CapaSdkRepository newCapaRepo() {
    return new CapaRepo(capas, sequence);
  }

  private static final class BidRepo extends InMemorySdkRepository<BidEntity>
      implements BidSdkRepository {

    private BidRepo(Map<Long, BidEntity> store, AtomicLong sequence) {
      super(store, sequence);
    }
  }

  private static final class QuotationRepo extends InMemorySdkRepository<QuotationEntity>
      implements QuotationSdkRepository {

    private QuotationRepo(Map<Long, QuotationEntity> store, AtomicLong sequence) {
      super(store, sequence);
    }
  }

  private static final class AwardRepo extends InMemorySdkRepository<AwardEntity>
      implements AwardSdkRepository {

    private AwardRepo(Map<Long, AwardEntity> store, AtomicLong sequence) {
      super(store, sequence);
    }
  }

  private static final class CapaRepo extends InMemorySdkRepository<CapaEntity>
      implements CapaSdkRepository {

    private CapaRepo(Map<Long, CapaEntity> store, AtomicLong sequence) {
      super(store, sequence);
    }
  }
}
