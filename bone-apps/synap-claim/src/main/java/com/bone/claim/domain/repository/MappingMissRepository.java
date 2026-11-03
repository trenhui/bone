package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.mapping.MappingMiss;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 未命中队列仓储。唯一键是 {@code (租户, 保司, 域, 原始码)}，一码一行。 */
@TenantScope(TenantScopeMode.AUTO)
public interface MappingMissRepository extends Repository<MappingMiss, Long> {

  default MappingMiss findByKey(Long tenantId, String insurerCode, String domain, String rawCode) {
    return findByCriteria(
            Criteria.<MappingMiss>create()
                .eq(MappingMiss::getTenantId, tenantId)
                .eq(MappingMiss::getInsurerCode, insurerCode)
                .eq(MappingMiss::getDomain, domain)
                .eq(MappingMiss::getRawCode, rawCode))
        .stream()
        .findFirst()
        .orElse(null);
  }

  /**
   * 管理面分页：默认按 last_seen_at 降序（最近发生的优先处置）；{@code sortBy=occurCount} 按频次降序 （高频缺口优先修，长尾才修得动）。未知排序值一律回落
   * last_seen_at，不抛错——管理台不因排序参数打摆。
   */
  default List<MappingMiss> findPage(
      Long tenantId, String status, String sortBy, int page, int size) {
    Criteria<MappingMiss> criteria =
        Criteria.<MappingMiss>create().eq(MappingMiss::getTenantId, tenantId);
    if (status != null && !status.isBlank()) {
      criteria.eq(MappingMiss::getStatus, status);
    }
    if ("occurCount".equals(sortBy)) {
      criteria.orderByDesc(MappingMiss::getOccurCount).orderByDesc(MappingMiss::getLastSeenAt);
    } else {
      criteria.orderByDesc(MappingMiss::getLastSeenAt);
    }
    criteria.page(page, size);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String status) {
    Criteria<MappingMiss> criteria =
        Criteria.<MappingMiss>create().eq(MappingMiss::getTenantId, tenantId);
    if (status != null && !status.isBlank()) {
      criteria.eq(MappingMiss::getStatus, status);
    }
    return countByCriteria(criteria);
  }

  /** 未关闭的未命中数：STP 闸门输入（该保司该域是否允许自动理算）。 */
  default long countOpen(Long tenantId, String insurerCode, String domain) {
    return countByCriteria(
        Criteria.<MappingMiss>create()
            .eq(MappingMiss::getTenantId, tenantId)
            .eq(MappingMiss::getInsurerCode, insurerCode)
            .eq(MappingMiss::getDomain, domain)
            .eq(MappingMiss::getStatus, "OPEN"));
  }

  /** 近 N 天新增（首次出现）未命中数：健康度趋势的分子，聚合查询不建新表。 */
  default long countFirstSeenSince(
      Long tenantId, String insurerCode, String domain, java.time.Instant since) {
    return countByCriteria(
        Criteria.<MappingMiss>create()
            .eq(MappingMiss::getTenantId, tenantId)
            .eq(MappingMiss::getInsurerCode, insurerCode)
            .eq(MappingMiss::getDomain, domain)
            .gte(MappingMiss::getFirstSeenAt, since));
  }
}
