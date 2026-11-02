package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.config.ClaimFlowConfig;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 租户级流程配置仓储（{@code t_claim_flow_config}，§6.7）。
 *
 * <p>按 {@code (tenant_id, flow_key)} 定位唯一生效配置；变更须带 {@code config_version} 发版号（乐观锁整体覆盖）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimFlowConfigRepository extends Repository<ClaimFlowConfig, Long> {

  /** 按租户 + 流程键定位唯一配置（UK {@code uk_tenant_flow}）。 */
  default ClaimFlowConfig findByFlowKey(Long tenantId, String flowKey) {
    Criteria<ClaimFlowConfig> criteria =
        Criteria.<ClaimFlowConfig>create()
            .eq(ClaimFlowConfig::getTenantId, tenantId)
            .eq(ClaimFlowConfig::getFlowKey, flowKey);
    return findOneByCriteria(criteria);
  }
}
