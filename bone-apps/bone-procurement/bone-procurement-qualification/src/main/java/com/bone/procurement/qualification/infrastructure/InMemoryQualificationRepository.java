package com.bone.procurement.qualification.infrastructure;

import com.bone.procurement.qualification.domain.gateway.QualificationRepository;
import com.bone.procurement.qualification.domain.model.qualification.Onboarding;
import com.bone.procurement.qualification.domain.model.qualification.OnboardingStatus;
import com.bone.procurement.qualification.domain.model.qualification.Qualification;
import com.bone.procurement.qualification.domain.model.qualification.VerifyStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 受控内存仓储（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated gateway 端口已由 {@link QualificationRepositoryAdapter} 委托 SDK 仓储实现；同接口保留 @Repository
 *     bean 会双 bean。
 */
@Deprecated
public class InMemoryQualificationRepository implements QualificationRepository {

  private final Map<String, Qualification> qualifications = new ConcurrentHashMap<>();
  private final Map<String, Onboarding> onboardings = new ConcurrentHashMap<>();
  private final AtomicLong seq = new AtomicLong(1);

  @Override
  public Long save(Qualification q) {
    if (q.getId() == null) q.setId(seq.getAndIncrement());
    qualifications.put(
        q.getTenantId() + ":" + q.getSupplierId() + ":" + q.getCertType() + ":" + q.getCertNo(), q);
    return q.getId();
  }

  @Override
  public Long save(Onboarding o) {
    if (o.getId() == null) o.setId(seq.getAndIncrement());
    onboardings.put(o.getTenantId() + ":" + o.getOnboardingNo(), o);
    return o.getId();
  }

  @Override
  public Optional<Onboarding> findInReview(Long tenantId, String supplierId) {
    return onboardings.values().stream()
        .filter(
            o ->
                o.getTenantId().equals(tenantId)
                    && supplierId.equals(o.getSupplierId())
                    && OnboardingStatus.IN_REVIEW.name().equals(o.getStatus()))
        .findFirst();
  }

  @Override
  public Optional<Onboarding> findByNo(Long tenantId, String no) {
    return Optional.ofNullable(onboardings.get(tenantId + ":" + no));
  }

  @Override
  public List<Qualification> findExpiring(Long tenantId) {
    long horizon = System.currentTimeMillis() + 30L * 86400000L;
    Date limit = new Date(horizon);
    List<Qualification> out = new ArrayList<>();
    for (Qualification q : qualifications.values()) {
      if (q.getTenantId().equals(tenantId)
          && q.getExpiresAt() != null
          && q.getExpiresAt().before(limit)
          && !VerifyStatus.EXPIRED.name().equals(q.getVerifyStatus())) {
        out.add(q);
      }
    }
    return out;
  }
}
