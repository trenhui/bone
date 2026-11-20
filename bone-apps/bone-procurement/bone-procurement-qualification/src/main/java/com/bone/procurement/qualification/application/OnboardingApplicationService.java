package com.bone.procurement.qualification.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.qualification.domain.gateway.QualificationRepository;
import com.bone.procurement.qualification.domain.model.qualification.Onboarding;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** 准入应用服务（骨架）：AI 评分只作建议，终决必须具名人工。 */
@Service
@NoDomainEvent
public class OnboardingApplicationService {

  private final QualificationRepository repository;

  public OnboardingApplicationService(QualificationRepository repository) {
    this.repository = repository;
  }

  public Long start(
      Long tenantId, String supplierId, String no, String backgroundRef, BigDecimal aiScore) {
    if (repository.findInReview(tenantId, supplierId).isPresent()) {
      throw new IllegalStateException("该供应商已有 IN_REVIEW 准入单（INV-13）");
    }
    return repository.save(Onboarding.start(tenantId, supplierId, no, backgroundRef, aiScore));
  }

  /** 人工终决：operator 来自登录态，不接受请求自报。 */
  public void approveByHuman(Long tenantId, String no, String operator) {
    Onboarding o =
        repository.findByNo(tenantId, no).orElseThrow(() -> new IllegalArgumentException("准入单不存在"));
    o.submitForReviewIfDraft();
    o.approveByHuman(operator);
    repository.save(o);
  }
}
