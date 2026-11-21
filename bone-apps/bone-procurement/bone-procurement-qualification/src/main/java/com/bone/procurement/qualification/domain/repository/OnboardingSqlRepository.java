package com.bone.procurement.qualification.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.qualification.domain.model.qualification.Onboarding;
import com.bone.procurement.qualification.domain.model.qualification.OnboardingStatus;
import java.util.List;
import java.util.Optional;

/** 准入单写侧仓储：IN_REVIEW 唯一性与按单号查询落在 default 方法。 */
public interface OnboardingSqlRepository extends Repository<Onboarding, Long> {

  /** IN-13：同租户同供应商至多一个 IN_REVIEW；查询条件即白名单口径。 */
  default Optional<Onboarding> findInReview(Long tenantId, String supplierId) {
    List<Onboarding> list =
        findByCriteria(
            Criteria.<Onboarding>create()
                .entityClass(Onboarding.class)
                .eq("tenantId", tenantId)
                .eq("supplierId", supplierId)
                .eq("status", OnboardingStatus.IN_REVIEW.name()));
    return list.stream().findFirst();
  }

  default Optional<Onboarding> findByNo(Long tenantId, String no) {
    List<Onboarding> list =
        findByCriteria(
            Criteria.<Onboarding>create()
                .entityClass(Onboarding.class)
                .eq("tenantId", tenantId)
                .eq("onboardingNo", no));
    return list.stream().findFirst();
  }
}
