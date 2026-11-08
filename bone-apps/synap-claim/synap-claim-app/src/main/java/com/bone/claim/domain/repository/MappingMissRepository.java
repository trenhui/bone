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
      Long tenantId, String status, String sortBy, String insurerCode, int page, int size) {
    Criteria<MappingMiss> criteria =
        Criteria.<MappingMiss>create().eq(MappingMiss::getTenantId, tenantId);
    if (status != null && !status.isBlank()) {
      criteria.eq(MappingMiss::getStatus, status);
    }
    if (insurerCode != null && !insurerCode.isBlank()) {
      criteria.eq(MappingMiss::getInsurerCode, insurerCode);
    }
    if ("occurCount".equals(sortBy)) {
      criteria.orderByDesc(MappingMiss::getOccurCount).orderByDesc(MappingMiss::getLastSeenAt);
    } else {
      criteria.orderByDesc(MappingMiss::getLastSeenAt);
    }
    criteria.page(page, size);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String status, String insurerCode) {
    Criteria<MappingMiss> criteria =
        Criteria.<MappingMiss>create().eq(MappingMiss::getTenantId, tenantId);
    if (status != null && !status.isBlank()) {
      criteria.eq(MappingMiss::getStatus, status);
    }
    if (insurerCode != null && !insurerCode.isBlank()) {
      criteria.eq(MappingMiss::getInsurerCode, insurerCode);
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

  /** 指定状态未命中数（命中率代理：RESOLVED / OPEN 分别计数，不建新表）。 */
  default long countByStatus(Long tenantId, String insurerCode, String domain, String status) {
    return countByCriteria(
        Criteria.<MappingMiss>create()
            .eq(MappingMiss::getTenantId, tenantId)
            .eq(MappingMiss::getInsurerCode, insurerCode)
            .eq(MappingMiss::getDomain, domain)
            .eq(MappingMiss::getStatus, status));
  }

  /**
   * 近 N 天纠错（reopen）事件数：{@code reopen_count>0} 且最近一次发生（last_seen_at）落在窗口内。
   * reopen=「以为修好其实没修好」，是比新增未命中更危险的高危信号（§6.9）。
   */
  default long countReopenedSince(
      Long tenantId, String insurerCode, String domain, java.time.Instant since) {
    return countByCriteria(
        Criteria.<MappingMiss>create()
            .eq(MappingMiss::getTenantId, tenantId)
            .eq(MappingMiss::getInsurerCode, insurerCode)
            .eq(MappingMiss::getDomain, domain)
            .gt(MappingMiss::getReopenCount, 0)
            .gte(MappingMiss::getLastSeenAt, since));
  }

  /** 该 (保司,域) 全量未命中（STP 赔案级直通率代理：按 last_case_no 去重计数，无 DISTINCT SQL）。 */
  default List<MappingMiss> findAll(Long tenantId, String insurerCode, String domain) {
    return findByCriteria(
        Criteria.<MappingMiss>create()
            .eq(MappingMiss::getTenantId, tenantId)
            .eq(MappingMiss::getInsurerCode, insurerCode)
            .eq(MappingMiss::getDomain, domain));
  }
}
