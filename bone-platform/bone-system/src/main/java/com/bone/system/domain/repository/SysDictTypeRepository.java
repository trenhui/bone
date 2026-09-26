package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.system.domain.model.dict.SysDictType;
import java.util.List;
import java.util.Optional;

/**
 * 字典类型仓储端口。
 *
 * <p><b>为什么查询要 {@code disableTenantFilter} + 显式 {@code in(tenant_id, 0, 当前租户)}</b>： 字典是「平台共享 +
 * 租户覆盖」模型，租户必须同时看到 {@code tenant_id=0} 的平台类型与自己的类型。 SDK 默认注入的租户谓词是单值等值，会直接过滤掉平台行——沿用默认通道就看不到平台字典，
 * 所以这里必须显式逃生（ADR-0029 的 {@code disableTenantFilter} 正是为这种平台+租户跨读场景而设）。
 *
 * <p><b>为什么类型是「全量拉 + 内存合并」而不是 SQL 分页</b>：覆盖模型要求按 {@code code} 用租户行覆盖平台行，SQL 侧无法表达「同 code
 * 去重并择优」；而字典类型的基数量级在百级， 全量拉取后合并再分页，比在 SQL 里写窗口函数更可读也更好测。
 */
public interface SysDictTypeRepository extends Repository<SysDictType, Long> {

  /** 平台类型（{@code tenant_id=0}）+ 指定租户自有类型的并集。 */
  default List<SysDictType> listAllTenants(Long tenantId) {
    return findByCriteria(
        Criteria.<SysDictType>create()
            .entityClass(SysDictType.class)
            .disableTenantFilter()
            .in(SysDictType::getTenantId, tenantScopeValues(tenantId))
            .orderByAsc(SysDictType::getSort)
            .orderByAsc(SysDictType::getCode));
  }

  /**
   * 按编码取类型：租户自有优先于平台（{@code order by tenant_id desc} 后取首行）。
   *
   * @return 租户行存在则返租户行，否则返平台行
   */
  default Optional<SysDictType> findByCodeAllTenants(String code, Long tenantId) {
    return findByCriteria(
            Criteria.<SysDictType>create()
                .entityClass(SysDictType.class)
                .disableTenantFilter()
                .eq(SysDictType::getCode, code)
                .in(SysDictType::getTenantId, tenantScopeValues(tenantId))
                .orderByDesc(SysDictType::getTenantId))
        .stream()
        .findFirst();
  }

  /** 租户作用域取值：恒含平台（0），租户为 0 时去重。 */
  private static Object[] tenantScopeValues(Long tenantId) {
    long tenant = tenantId == null ? 0L : tenantId;
    return tenant == 0L ? new Object[] {0L} : new Object[] {0L, tenant};
  }
}
