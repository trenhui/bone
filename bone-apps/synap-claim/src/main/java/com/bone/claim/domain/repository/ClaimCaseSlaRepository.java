package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.sla.ClaimCaseSla;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * SLA 计时聚合仓储（ADR-003 / §6.6 运营读模型）。
 *
 * <p>可变更读模型：{@code save} 按 {@code (tenant_id, claim_id)} 唯一 upsert，{@link ClaimCaseSla} 的
 * {@code @Version} 乐观锁防并发刷新（两个调度实例同时物化同一案件时后者冲突重试）。
 */
public interface ClaimCaseSlaRepository extends Repository<ClaimCaseSla, Long> {

  /** 按租户 + 案件定位唯一 SLA 快照（UK `uk_claim`）。 */
  default ClaimCaseSla findByClaimId(Long tenantId, long claimId) {
    Criteria<ClaimCaseSla> criteria =
        Criteria.<ClaimCaseSla>create()
            .eq(ClaimCaseSla::getTenantId, tenantId)
            .eq(ClaimCaseSla::getClaimId, claimId);
    return findOneByCriteria(criteria);
  }
}
