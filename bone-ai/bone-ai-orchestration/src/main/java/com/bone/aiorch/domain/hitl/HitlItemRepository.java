package com.bone.aiorch.domain.hitl;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.Optional;

/**
 * HITL 待裁决项仓储：{@code extends Repository} 由 bone-metadata-sdk 动态代理（{@code @EnableSqlRepositories} 扫描
 * “*Repository”接口即生成实现），无需手写实现类（HC-001 唯一持久化方案，与 RuleAssetRepository 同款）。
 *
 * <p>内置 CRUD（findById/insert/save）直接继承；自定义查询用 {@code default} + QueryBuilder（租户 / 软删由
 * TenantFilterInjector 自动注入）。
 */
public interface HitlItemRepository extends Repository<HitlItem, String> {

  /** 租户内待裁决列表（PENDING，可叠加场景/风险过滤；租户自动注入）。 */
  default List<HitlItem> findPending(String tenantId, String scene, String riskLevel) {
    List<HitlItem> pending =
        QueryBuilder.from(HitlItem.class).where(HitlItem::getStatus).eq(HitlStatus.PENDING).list();
    return pending.stream()
        .filter(i -> scene == null || scene.isBlank() || scene.equals(i.getScene()))
        .filter(i -> riskLevel == null || riskLevel.isBlank() || riskLevel.equals(i.getRiskLevel()))
        .toList();
  }

  /** 按 ID 取待裁决项（PENDING）；无则返回空。 */
  default Optional<HitlItem> findPendingById(String tenantId, String id) {
    HitlItem item = findById(id);
    if (item == null || item.getStatus() != HitlStatus.PENDING) {
      return Optional.empty();
    }
    return Optional.of(item);
  }
}
