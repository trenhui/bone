package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 租户级特性开关仓储（{@code t_feature_toggle}，§11.4 三态开关）。
 *
 * <p>仅提供按 {@code (tenant_id, feature_key)} 定位与追加，无 UPDATE/DELETE 之外的特殊路径；开关变更走乐观锁整体覆盖。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface FeatureToggleRepository extends Repository<FeatureToggle, Long> {

  /** 按租户 + 特性键定位唯一开关（UK {@code uk_tenant_feature}）。 */
  default FeatureToggle findByFeatureKey(Long tenantId, String featureKey) {
    Criteria<FeatureToggle> criteria =
        Criteria.<FeatureToggle>create()
            .eq(FeatureToggle::getTenantId, tenantId)
            .eq(FeatureToggle::getFeatureKey, featureKey);
    return findOneByCriteria(criteria);
  }
}
