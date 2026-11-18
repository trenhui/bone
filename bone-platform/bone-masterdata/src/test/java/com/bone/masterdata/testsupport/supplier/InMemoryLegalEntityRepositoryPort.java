package com.bone.masterdata.testsupport.supplier;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.gateway.LegalEntityRepositoryPort;
import com.bone.masterdata.domain.model.supplier.LegalEntity;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版法人身份登记：按 (租户, 统一社会信用代码) 与 (租户, partyId) 双索引，用于验证「身份主键唯一性」与乐观版本。
 *
 * <p>与其他夹具一致：只替换持久化机制，不改变端口契约。生产 adapter 与 {@code t_proc_*} 属 R-2。
 */
public final class InMemoryLegalEntityRepositoryPort implements LegalEntityRepositoryPort {

  private final Map<String, LegalEntity> byParty = new ConcurrentHashMap<>();

  @Override
  public Optional<LegalEntity> findByUnifiedSocialCreditCode(
      long tenantId, String unifiedSocialCreditCode) {
    if (unifiedSocialCreditCode == null) {
      return Optional.empty();
    }
    return byParty.values().stream()
        .filter(e -> e.tenantId() == tenantId)
        .filter(e -> unifiedSocialCreditCode.equalsIgnoreCase(e.unifiedSocialCreditCode()))
        .findFirst();
  }

  @Override
  public Optional<LegalEntity> findByTaxId(long tenantId, String taxId) {
    if (taxId == null) {
      return Optional.empty();
    }
    return byParty.values().stream()
        .filter(e -> e.tenantId() == tenantId)
        .filter(e -> taxId.equalsIgnoreCase(e.taxId()))
        .findFirst();
  }

  @Override
  public Optional<LegalEntity> findByPartyId(long tenantId, String partyId) {
    return Optional.ofNullable(byParty.get(key(tenantId, partyId)));
  }

  @Override
  public void save(LegalEntity legalEntity, long expectedVersion) {
    String k = key(legalEntity.tenantId(), legalEntity.partyId());
    LegalEntity current = byParty.get(k);
    long actual = current == null ? 0L : current.version();
    if (actual != expectedVersion) {
      throw new DomainException(
          "并发版本冲突：partyId=" + legalEntity.partyId() + " 期望 " + expectedVersion + " 实际 " + actual);
    }
    byParty.put(k, legalEntity);
    saveCount++;
  }

  public int saveCount() {
    return saveCount;
  }

  public void reset() {
    byParty.clear();
    saveCount = 0;
  }

  private int saveCount;

  private static String key(long tenantId, String partyId) {
    return tenantId + "|" + partyId;
  }
}
