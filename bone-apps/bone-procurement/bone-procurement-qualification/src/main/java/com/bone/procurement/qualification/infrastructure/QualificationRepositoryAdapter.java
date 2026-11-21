package com.bone.procurement.qualification.infrastructure;

import com.bone.procurement.qualification.domain.gateway.QualificationRepository;
import com.bone.procurement.qualification.domain.model.Onboarding;
import com.bone.procurement.qualification.domain.model.Qualification;
import com.bone.procurement.qualification.domain.repository.OnboardingSqlRepository;
import com.bone.procurement.qualification.domain.repository.QualificationSqlRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 准入仓储适配器：实现 domain/gateway 端口，把方法委托给 SDK 仓储。 */
@Component
public class QualificationRepositoryAdapter implements QualificationRepository {

  private final OnboardingSqlRepository onboardings;
  private final QualificationSqlRepository qualifications;

  public QualificationRepositoryAdapter(
      OnboardingSqlRepository onboardings, QualificationSqlRepository qualifications) {
    this.onboardings = onboardings;
    this.qualifications = qualifications;
  }

  @Override
  public Long save(Qualification qualification) {
    return qualifications.save(qualification);
  }

  @Override
  public Long save(Onboarding onboarding) {
    return onboardings.save(onboarding);
  }

  @Override
  public Optional<Onboarding> findInReview(Long tenantId, String supplierId) {
    return onboardings.findInReview(tenantId, supplierId);
  }

  @Override
  public Optional<Onboarding> findByNo(Long tenantId, String no) {
    return onboardings.findByNo(tenantId, no);
  }

  @Override
  public List<Qualification> findExpiring(Long tenantId) {
    return qualifications.findExpiring(tenantId);
  }
}
