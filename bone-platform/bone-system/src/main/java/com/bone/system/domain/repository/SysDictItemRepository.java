package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.system.domain.model.dict.SysDictItem;
import java.util.List;
import java.util.Optional;

/**
 * 字典项仓储端口（覆盖模型见 {@link SysDictTypeRepository} 的租户作用域说明）。
 *
 * <p>删除保护与环检测所需的「查子项 / 查父链」都建立在 {@link #listByTypeAllTenants} 的全量结果上：
 * 单类型字典项数量有限（千级以内），一次加载后在内存里做树组装，比逐层递归查库少 N 次往返。
 */
public interface SysDictItemRepository extends Repository<SysDictItem, Long> {

  /** 某类型在租户作用域内（平台 + 本租户）的全部项，按排序与编码升序。 */
  default List<SysDictItem> listByTypeAllTenants(String typeCode, Long tenantId) {
    return findByCriteria(
        Criteria.<SysDictItem>create()
            .entityClass(SysDictItem.class)
            .disableTenantFilter()
            .eq(SysDictItem::getTypeCode, typeCode)
            .in(SysDictItem::getTenantId, tenantScopeValues(tenantId))
            .orderByAsc(SysDictItem::getSort)
            .orderByAsc(SysDictItem::getCode));
  }

  /** 按类型 + 编码取项：租户覆盖行优先于平台行。 */
  default Optional<SysDictItem> findByTypeAndCodeAllTenants(
      String typeCode, String code, Long tenantId) {
    return findByCriteria(
            Criteria.<SysDictItem>create()
                .entityClass(SysDictItem.class)
                .disableTenantFilter()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getCode, code)
                .in(SysDictItem::getTenantId, tenantScopeValues(tenantId))
                .orderByDesc(SysDictItem::getTenantId))
        .stream()
        .findFirst();
  }

  /** 子项计数（删除保护：有子项的节点不可删）。 */
  default long countChildrenAllTenants(String typeCode, String parentCode, Long tenantId) {
    Long count =
        countByCriteria(
            Criteria.<SysDictItem>create()
                .entityClass(SysDictItem.class)
                .disableTenantFilter()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getParentCode, parentCode)
                .in(SysDictItem::getTenantId, tenantScopeValues(tenantId)));
    return count == null ? 0L : count;
  }

  /** 该类型已有项计数（删除类型前置校验）。 */
  default long countByTypeAllTenants(String typeCode, Long tenantId) {
    Long count =
        countByCriteria(
            Criteria.<SysDictItem>create()
                .entityClass(SysDictItem.class)
                .disableTenantFilter()
                .eq(SysDictItem::getTypeCode, typeCode)
                .in(SysDictItem::getTenantId, tenantScopeValues(tenantId)));
    return count == null ? 0L : count;
  }

  private static Object[] tenantScopeValues(Long tenantId) {
    long tenant = tenantId == null ? 0L : tenantId;
    return tenant == 0L ? new Object[] {0L} : new Object[] {0L, tenant};
  }
}
