package com.bone.airules.domain.repository;

import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.Optional;

/**
 * 规则资产仓储：{@code extends Repository} 由 bone-metadata-sdk 动态代理（{@code @EnableSqlRepositories} 扫描
 * “*Repository”接口即生成实现），**无需手写实现类**。
 *
 * <p>内置 CRUD（findById/insert/save/...）直接继承（{@code findById} 返回可空实体，包 Optional 由调用方处理）；自定义查询用 {@code
 * default} + QueryBuilder（租户 / 软删由 TenantFilterInjector 自动注入，非 @Sql 通道故无需 @TenantScope）。
 */
public interface RuleAssetRepository extends Repository<RuleAsset, String> {

  /** 按业务编码查询（租户内唯一；租户条件由 SDK 自动注入，入参 tenantId 仅作语义提示）。 */
  default Optional<RuleAsset> findByCode(String tenantId, String code) {
    List<RuleAsset> list =
        QueryBuilder.from(RuleAsset.class).where(RuleAsset::getCode).eq(code).list();
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  /** 租户内全部生效规则（ACTIVE，含灰度中；租户自动注入）。 */
  default List<RuleAsset> findActive(String tenantId) {
    return QueryBuilder.from(RuleAsset.class)
        .where(RuleAsset::getStatus)
        .eq(RuleStatus.ACTIVE)
        .list();
  }
}
