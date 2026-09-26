package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.system.domain.model.dict.SysDictHierarchy;
import java.util.List;
import java.util.Optional;

/**
 * 字典层级关系仓储（租户作用域口径见 {@link SysDictTypeRepository} 的说明）。
 *
 * <p>层级与值一样遵循「平台共享 + 租户覆盖」：租户可在自己的作用域内重挂父子关系， 而不改动平台层级。
 */
public interface SysDictHierarchyRepository extends Repository<SysDictHierarchy, Long> {

  /** 指定值域 + 指定层级视图的全部关系。 */
  default List<SysDictHierarchy> listByTypeAllTenants(
      String typeCode, String hierarchyCode, Long tenantId) {
    return findByCriteria(
        Criteria.<SysDictHierarchy>create()
            .entityClass(SysDictHierarchy.class)
            .disableTenantFilter()
            .eq(SysDictHierarchy::getTypeCode, typeCode)
            .eq(SysDictHierarchy::getHierarchyCode, hierarchyCode)
            .in(SysDictHierarchy::getTenantId, tenantScopeValues(tenantId))
            .orderByAsc(SysDictHierarchy::getLevel)
            .orderByAsc(SysDictHierarchy::getSort)
            .orderByAsc(SysDictHierarchy::getCode));
  }

  /** 该值域的全部层级视图（用于前端「层级视图」选择器）。 */
  default List<SysDictHierarchy> listAllViewsAllTenants(String typeCode, Long tenantId) {
    return findByCriteria(
        Criteria.<SysDictHierarchy>create()
            .entityClass(SysDictHierarchy.class)
            .disableTenantFilter()
            .eq(SysDictHierarchy::getTypeCode, typeCode)
            .in(SysDictHierarchy::getTenantId, tenantScopeValues(tenantId))
            .orderByAsc(SysDictHierarchy::getHierarchyCode));
  }

  /** 按编码取关系（租户覆盖优先）。 */
  default Optional<SysDictHierarchy> findByCodeAllTenants(
      String typeCode, String hierarchyCode, String code, Long tenantId) {
    return findByCriteria(
            Criteria.<SysDictHierarchy>create()
                .entityClass(SysDictHierarchy.class)
                .disableTenantFilter()
                .eq(SysDictHierarchy::getTypeCode, typeCode)
                .eq(SysDictHierarchy::getHierarchyCode, hierarchyCode)
                .eq(SysDictHierarchy::getCode, code)
                .in(SysDictHierarchy::getTenantId, tenantScopeValues(tenantId))
                .orderByDesc(SysDictHierarchy::getTenantId))
        .stream()
        .findFirst();
  }

  /** 子关系计数（删除保护）。 */
  default long countChildrenAllTenants(
      String typeCode, String hierarchyCode, String parentCode, Long tenantId) {
    Long count =
        countByCriteria(
            Criteria.<SysDictHierarchy>create()
                .entityClass(SysDictHierarchy.class)
                .disableTenantFilter()
                .eq(SysDictHierarchy::getTypeCode, typeCode)
                .eq(SysDictHierarchy::getHierarchyCode, hierarchyCode)
                .eq(SysDictHierarchy::getParentCode, parentCode)
                .in(SysDictHierarchy::getTenantId, tenantScopeValues(tenantId)));
    return count == null ? 0L : count;
  }

  /**
   * 移除某字典项在<b>全部</b>层级视图中的关系（删项时调用）。
   *
   * <p>用 {@code deleteByCriteria} 而非软删：关系是项的附属数据，项已删时留着关系只会让树里 出现无法渲染的空洞节点。
   */
  default int removeByCodeAllTenants(String typeCode, String code, Long tenantId) {
    return deleteByCriteria(
        Criteria.<SysDictHierarchy>create()
            .entityClass(SysDictHierarchy.class)
            .disableTenantFilter()
            .eq(SysDictHierarchy::getTypeCode, typeCode)
            .eq(SysDictHierarchy::getCode, code)
            .in(SysDictHierarchy::getTenantId, tenantScopeValues(tenantId)));
  }

  private static Object[] tenantScopeValues(Long tenantId) {
    long tenant = tenantId == null ? 0L : tenantId;
    return tenant == 0L ? new Object[] {0L} : new Object[] {0L, tenant};
  }
}
