package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.mapping.CodeMapping;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 保司码-标准码映射仓储。写侧只有"插新行 / 关闭旧行"，不提供改标准码的入口。 */
@TenantScope(TenantScopeMode.AUTO)
public interface CodeMappingRepository extends Repository<CodeMapping, Long> {

  /**
   * 解析候选：返回该 (保司, 域, 保司码) 下<b>全部</b>行（含历史），由内核按 ACTIVE 过滤。
   *
   * <p><b>为什么把过滤放在内核而不是 SQL</b>：候选集很小（同码通常 ≤ 3 行），而"哪些行算有效"是 业务判定；放进查询会让"停用后仍能解释历史"这条不变量散落在两处。
   */
  default List<CodeMapping> findCandidates(
      Long tenantId, String insurerCode, String domain, String insurerItemCode) {
    return findByCriteria(
        Criteria.<CodeMapping>create()
            .eq(CodeMapping::getTenantId, tenantId)
            .eq(CodeMapping::getInsurerCode, insurerCode)
            .eq(CodeMapping::getDomain, domain)
            .eq(CodeMapping::getInsurerItemCode, insurerItemCode));
  }

  /** 当前生效行（发布前查重：同键同消歧键只能有一条 ACTIVE）。 */
  default CodeMapping findActive(
      Long tenantId,
      String insurerCode,
      String domain,
      String insurerItemCode,
      String disambiguationKey) {
    return findByCriteria(
            Criteria.<CodeMapping>create()
                .eq(CodeMapping::getTenantId, tenantId)
                .eq(CodeMapping::getInsurerCode, insurerCode)
                .eq(CodeMapping::getDomain, domain)
                .eq(CodeMapping::getInsurerItemCode, insurerItemCode)
                .eq(CodeMapping::getDisambiguationKey, disambiguationKey)
                .eq(CodeMapping::getActiveFlag, "Y"))
        .stream()
        .findFirst()
        .orElse(null);
  }

  /** 反查：该保司该域下指向某标准码的全部行（含历史，用于回答"这个标准码以前对过哪些码"）。 */
  default List<CodeMapping> findReverse(
      Long tenantId, String insurerCode, String domain, String standardCode) {
    return findByCriteria(
        Criteria.<CodeMapping>create()
            .eq(CodeMapping::getTenantId, tenantId)
            .eq(CodeMapping::getInsurerCode, insurerCode)
            .eq(CodeMapping::getDomain, domain)
            .eq(CodeMapping::getStandardCode, standardCode));
  }

  /** 发布批次内的全部映射（回放/影响面分析的最小数据集）。 */
  default List<CodeMapping> findByRelease(Long tenantId, String versionNo) {
    return findByCriteria(
        Criteria.<CodeMapping>create()
            .eq(CodeMapping::getTenantId, tenantId)
            .eq(CodeMapping::getVersionNo, versionNo));
  }

  default List<CodeMapping> findPage(
      Long tenantId, String insurerCode, String domain, int page, int size) {
    Criteria<CodeMapping> criteria =
        Criteria.<CodeMapping>create().eq(CodeMapping::getTenantId, tenantId);
    if (insurerCode != null && !insurerCode.isBlank()) {
      criteria.eq(CodeMapping::getInsurerCode, insurerCode);
    }
    if (domain != null && !domain.isBlank()) {
      criteria.eq(CodeMapping::getDomain, domain);
    }
    criteria.eq(CodeMapping::getActiveFlag, "Y").page(page, size);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String insurerCode, String domain) {
    Criteria<CodeMapping> criteria =
        Criteria.<CodeMapping>create()
            .eq(CodeMapping::getTenantId, tenantId)
            .eq(CodeMapping::getActiveFlag, "Y");
    if (insurerCode != null && !insurerCode.isBlank()) {
      criteria.eq(CodeMapping::getInsurerCode, insurerCode);
    }
    if (domain != null && !domain.isBlank()) {
      criteria.eq(CodeMapping::getDomain, domain);
    }
    return countByCriteria(criteria);
  }
}
