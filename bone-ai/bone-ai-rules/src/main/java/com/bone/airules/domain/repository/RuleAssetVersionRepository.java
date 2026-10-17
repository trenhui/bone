package com.bone.airules.domain.repository;

import com.bone.airules.domain.model.RuleAssetVersion;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 规则版本链仓储：{@code extends Repository} 由 bone-metadata-sdk 动态代理（{@code @EnableSqlRepositories} 扫描
 * “*Repository”接口即生成实现），无需手写实现类（HC-001 唯一持久化方案）。
 *
 * <p>租户 / 软删由 TenantFilterInjector 自动注入；自定义查询用 {@code default} + QueryBuilder。
 */
public interface RuleAssetVersionRepository extends Repository<RuleAssetVersion, String> {

  /** 某规则的全部版本（版本号降序——最新在前，供回放 / diff / 回滚选版）。 */
  default List<RuleAssetVersion> findByRuleId(String ruleId) {
    List<RuleAssetVersion> list =
        QueryBuilder.from(RuleAssetVersion.class)
            .where(RuleAssetVersion::getRuleId)
            .eq(ruleId)
            .list();
    list.sort(Comparator.comparingInt(RuleAssetVersion::getVersionNo).reversed());
    return list;
  }

  /** 取指定版本（回滚源）。 */
  default Optional<RuleAssetVersion> findByRuleIdAndVersionNo(String ruleId, int versionNo) {
    return findByRuleId(ruleId).stream().filter(v -> v.getVersionNo() == versionNo).findFirst();
  }
}
