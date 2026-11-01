package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.liability.LiabilityFeeRule;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 责任费用类别规则仓储（按 liabilityUuid 全量读写，保存走"软删旧行 + 插新行"的全量替换）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface LiabilityFeeRuleRepository extends Repository<LiabilityFeeRule, Long> {

  default List<LiabilityFeeRule> findByLiabilityUuid(Long tenantId, String liabilityUuid) {
    Criteria<LiabilityFeeRule> criteria =
        Criteria.<LiabilityFeeRule>create()
            .eq(LiabilityFeeRule::getTenantId, tenantId)
            .eq(LiabilityFeeRule::getLiabilityUuid, liabilityUuid);
    criteria.orderByAsc(LiabilityFeeRule::getId);
    return findByCriteria(criteria);
  }

  /** 软删该责任全部费用规则（全量替换的前半步；SDK 按 @Table 注解走软删）。 */
  default void removeByLiabilityUuid(Long tenantId, String liabilityUuid) {
    Criteria<LiabilityFeeRule> criteria =
        Criteria.<LiabilityFeeRule>create()
            .eq(LiabilityFeeRule::getTenantId, tenantId)
            .eq(LiabilityFeeRule::getLiabilityUuid, liabilityUuid);
    deleteByCriteria(criteria);
  }
}
