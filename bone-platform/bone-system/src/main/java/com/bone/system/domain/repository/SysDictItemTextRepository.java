package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.system.domain.model.dict.SysDictItemText;
import java.util.List;
import java.util.Optional;

/**
 * 字典项译文仓储（SAP {@code T005T} 风格副表）。
 *
 * <p>读侧有一个专门入口 {@link #listByTypeAndLanguageAllTenants}：给一个值域的下拉数据配译文时， 按「值域 +
 * 语言」一次取回全部译文，避免每个选项一次查询（N+1）。
 */
public interface SysDictItemTextRepository extends Repository<SysDictItemText, Long> {

  /** 某字典项的全部译文。 */
  default List<SysDictItemText> listByItemAllTenants(String typeCode, String code, Long tenantId) {
    return findByCriteria(
        Criteria.<SysDictItemText>create()
            .entityClass(SysDictItemText.class)
            .disableTenantFilter()
            .eq(SysDictItemText::getTypeCode, typeCode)
            .eq(SysDictItemText::getCode, code)
            .in(SysDictItemText::getTenantId, tenantScopeValues(tenantId))
            .orderByAsc(SysDictItemText::getLanguage));
  }

  /** 某值域在某语言下的全部译文（供 {@code /options?lang=} 一次取回）。 */
  default List<SysDictItemText> listByTypeAndLanguageAllTenants(
      String typeCode, String language, Long tenantId) {
    return findByCriteria(
        Criteria.<SysDictItemText>create()
            .entityClass(SysDictItemText.class)
            .disableTenantFilter()
            .eq(SysDictItemText::getTypeCode, typeCode)
            .eq(SysDictItemText::getLanguage, language)
            .in(SysDictItemText::getTenantId, tenantScopeValues(tenantId)));
  }

  default Optional<SysDictItemText> findByItemAndLanguageAllTenants(
      String typeCode, String code, String language, Long tenantId) {
    return findByCriteria(
            Criteria.<SysDictItemText>create()
                .entityClass(SysDictItemText.class)
                .disableTenantFilter()
                .eq(SysDictItemText::getTypeCode, typeCode)
                .eq(SysDictItemText::getCode, code)
                .eq(SysDictItemText::getLanguage, language)
                .in(SysDictItemText::getTenantId, tenantScopeValues(tenantId))
                .orderByDesc(SysDictItemText::getTenantId))
        .stream()
        .findFirst();
  }

  /** 移除某字典项的全部译文（删项时调用）。 */
  default int removeByItemAllTenants(String typeCode, String code, Long tenantId) {
    return deleteByCriteria(
        Criteria.<SysDictItemText>create()
            .entityClass(SysDictItemText.class)
            .disableTenantFilter()
            .eq(SysDictItemText::getTypeCode, typeCode)
            .eq(SysDictItemText::getCode, code)
            .in(SysDictItemText::getTenantId, tenantScopeValues(tenantId)));
  }

  private static Object[] tenantScopeValues(Long tenantId) {
    long tenant = tenantId == null ? 0L : tenantId;
    return tenant == 0L ? new Object[] {0L} : new Object[] {0L, tenant};
  }
}
