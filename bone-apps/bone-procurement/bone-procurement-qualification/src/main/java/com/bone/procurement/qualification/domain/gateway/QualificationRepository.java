package com.bone.procurement.qualification.domain.gateway;

import com.bone.procurement.qualification.domain.model.qualification.Onboarding;
import com.bone.procurement.qualification.domain.model.qualification.Qualification;
import java.util.List;
import java.util.Optional;

/** 准入仓储端口（白名单方法）。 */
public interface QualificationRepository {
  Long save(Qualification qualification);

  Long save(Onboarding onboarding);

  Optional<Onboarding> findInReview(Long tenantId, String supplierId);

  Optional<Onboarding> findByNo(Long tenantId, String no);

  List<Qualification> findExpiring(Long tenantId);
}
