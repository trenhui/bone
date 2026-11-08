package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.mapping.FieldBinding;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 字段-选项集绑定仓储：标准基线（{@code '*'}）与保司专属各一条。 */
@TenantScope(TenantScopeMode.AUTO)
public interface FieldBindingRepository extends Repository<FieldBinding, Long> {

  default FieldBinding findByFieldAndInsurer(Long tenantId, String fieldCode, String insurerCode) {
    return findByCriteria(
            Criteria.<FieldBinding>create()
                .eq(FieldBinding::getTenantId, tenantId)
                .eq(FieldBinding::getFieldCode, fieldCode)
                .eq(FieldBinding::getInsurerCode, insurerCode))
        .stream()
        .findFirst()
        .orElse(null);
  }

  default List<FieldBinding> findByInsurer(Long tenantId, String insurerCode) {
    return findByCriteria(
        Criteria.<FieldBinding>create()
            .eq(FieldBinding::getTenantId, tenantId)
            .eq(FieldBinding::getInsurerCode, insurerCode));
  }
}
