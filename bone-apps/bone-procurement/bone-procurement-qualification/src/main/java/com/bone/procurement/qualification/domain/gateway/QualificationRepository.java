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

  /** 按租户 + ID 读取证照；跨租户返回 empty（租户锚定，防越权复核）。退役内存实现不提供该能力。 */
  default Optional<Qualification> findById(Long tenantId, Long qualificationId) {
    return Optional.empty();
  }
}
