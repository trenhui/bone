package com.bone.procurement.qualification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.bone.procurement.qualification.domain.gateway.QualificationRepository;
import com.bone.procurement.qualification.domain.model.Onboarding;
import com.bone.procurement.qualification.domain.model.Qualification;
import com.bone.procurement.qualification.domain.model.VerifyStatus;
import com.bone.procurement.qualification.domain.repository.OnboardingSqlRepository;
import com.bone.procurement.qualification.domain.repository.QualificationSqlRepository;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 准入 gateway 适配器：findInReview / findExpiring 的过滤口径与新实例读回一致。 */
class QualificationRepositoryAdapterTest {

  @Test
  @DisplayName("findInReview 只取 IN_REVIEW，且按租户隔离")
  void findInReviewOnlyReturnsInReview() {
    FakeSdkStore<Onboarding> onboardings = new FakeSdkStore<>();
    FakeSdkStore<Qualification> qualifications = new FakeSdkStore<>();
    QualificationRepository port = adapter(onboardings, qualifications);

    Onboarding inReview = Onboarding.start(1L, "SUP-1", "ONB-1", "bg-1", new BigDecimal("80"));
    inReview.submitForReview();
    port.save(inReview);
    port.save(Onboarding.start(1L, "SUP-1", "ONB-2", "bg-2", new BigDecimal("90")));
    Onboarding otherTenant = Onboarding.start(2L, "SUP-1", "ONB-3", "bg-3", new BigDecimal("70"));
    otherTenant.submitForReview();
    port.save(otherTenant);

    Onboarding actual = port.findInReview(1L, "SUP-1").orElseThrow();
    assertThat(actual.getOnboardingNo()).isEqualTo("ONB-1");
    assertThat(actual.getStatus()).isEqualTo("IN_REVIEW");
    assertThat(port.findInReview(1L, "SUP-9")).isEmpty();
    assertThat(port.findInReview(2L, "SUP-1").orElseThrow().getOnboardingNo()).isEqualTo("ONB-3");
  }

  @Test
  @DisplayName("findExpiring 只取 30 天内到期证照，排除 EXPIRED 与其它租户")
  void findExpiringExcludesExpired() {
    FakeSdkStore<Onboarding> onboardings = new FakeSdkStore<>();
    FakeSdkStore<Qualification> qualifications = new FakeSdkStore<>();
    QualificationRepository port = adapter(onboardings, qualifications);

    long now = System.currentTimeMillis();
    Qualification soon =
        Qualification.submit(
            1L, "SUP-1", "ISO", "***-1", new Date(now + 10 * 86400000L), "att-1", "{}");
    port.save(soon);

    Qualification expired =
        Qualification.submit(1L, "SUP-1", "ISO", "***-2", new Date(now - 86400000L), "att-2", "{}");
    expired.markExpiredIfDue(new Date());
    port.save(expired);

    port.save(
        Qualification.submit(
            1L, "SUP-1", "ISO", "***-3", new Date(now + 60 * 86400000L), "att-3", "{}"));
    port.save(
        Qualification.submit(
            2L, "SUP-1", "ISO", "***-4", new Date(now + 10 * 86400000L), "att-4", "{}"));

    List<Qualification> actual = port.findExpiring(1L);
    assertThat(actual).hasSize(1);
    assertThat(actual.get(0).getCertNo()).isEqualTo("***-1");
    assertThat(actual.get(0).getVerifyStatus()).isEqualTo(VerifyStatus.PENDING.name());
    assertThat(port.findExpiring(9L)).isEmpty();
  }

  @Test
  @DisplayName("save 两个重载与 findByNo 均委托 SDK 仓储")
  void saveAndFindByNoDelegateToSdk() {
    FakeSdkStore<Onboarding> onboardings = new FakeSdkStore<>();
    FakeSdkStore<Qualification> qualifications = new FakeSdkStore<>();
    QualificationRepository port = adapter(onboardings, qualifications);

    Long certId =
        port.save(Qualification.submit(1L, "SUP-2", "ISO", "***-9", new Date(), "att-9", "{}"));
    Long onboardingId = port.save(Onboarding.start(1L, "SUP-2", "ONB-9", "bg-9", BigDecimal.ONE));

    assertThat(certId).isNotNull();
    assertThat(onboardingId).isNotNull();
    assertThat(port.findByNo(1L, "ONB-9").orElseThrow().getId()).isEqualTo(onboardingId);
    assertThat(port.findByNo(1L, "MISSING")).isEmpty();
  }

  @Test
  @DisplayName("新适配器实例（模拟重启）读回同一份数据，字段一致")
  void newAdapterInstanceReadsBackSameState() {
    FakeSdkStore<Onboarding> onboardings = new FakeSdkStore<>();
    FakeSdkStore<Qualification> qualifications = new FakeSdkStore<>();
    QualificationRepository first = adapter(onboardings, qualifications);

    Onboarding onboarding = Onboarding.start(5L, "SUP-5", "ONB-5", "bg-5", new BigDecimal("55"));
    onboarding.submitForReview();
    Long id = first.save(onboarding);

    QualificationRepository restarted = adapter(onboardings, qualifications);
    Onboarding actual = restarted.findByNo(5L, "ONB-5").orElseThrow();
    assertThat(actual.getId()).isEqualTo(id);
    assertThat(actual.getTenantId()).isEqualTo(5L);
    assertThat(actual.getSupplierId()).isEqualTo("SUP-5");
    assertThat(actual.getScore()).isEqualByComparingTo(new BigDecimal("55"));
    assertThat(actual.getStatus()).isEqualTo("IN_REVIEW");
  }

  private QualificationRepository adapter(
      FakeSdkStore<Onboarding> onboardings, FakeSdkStore<Qualification> qualifications) {
    OnboardingSqlRepository onboardingRepo =
        mock(OnboardingSqlRepository.class, CALLS_REAL_METHODS);
    doAnswer(invocation -> onboardings.save(invocation.getArgument(0)))
        .when(onboardingRepo)
        .save(any());
    doAnswer(invocation -> onboardings.findByCriteria(invocation.getArgument(0)))
        .when(onboardingRepo)
        .findByCriteria(any());

    QualificationSqlRepository qualificationRepo =
        mock(QualificationSqlRepository.class, CALLS_REAL_METHODS);
    doAnswer(invocation -> qualifications.save(invocation.getArgument(0)))
        .when(qualificationRepo)
        .save(any());
    doAnswer(invocation -> qualifications.findByCriteria(invocation.getArgument(0)))
        .when(qualificationRepo)
        .findByCriteria(any());

    return new QualificationRepositoryAdapter(onboardingRepo, qualificationRepo);
  }
}
