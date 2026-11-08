package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bone.claim.application.PolicyDetailApplicationService.PolicyDetail;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.ClaimCoverageOverride;
import com.bone.claim.domain.model.liability.LiabilityFeeRule;
import com.bone.claim.domain.model.mapping.ClaimInsurer;
import com.bone.claim.domain.model.mapping.MasterDataDomain;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimInsurerRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.CodeMappingRepository;
import com.bone.claim.domain.repository.CoverageOverrideRepository;
import com.bone.claim.domain.repository.FieldBindingRepository;
import com.bone.claim.domain.repository.InsurerCodeItemRepository;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.claim.domain.repository.MappingMissRepository;
import com.bone.claim.domain.repository.RefDiseaseRepository;
import com.bone.claim.domain.repository.RefDrugRepository;
import com.bone.claim.domain.repository.RefFeeCategoryRepository;
import com.bone.claim.domain.repository.RefHospitalRepository;
import com.bone.claim.domain.repository.StdCodeAliasRepository;
import com.bone.claim.domain.repository.StdCodeSplitRepository;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 保单聚合详情（task 4）：主体健康度四域 + 计划→责任树 + 覆盖差异行；主体为空的存量保单不含健康度。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyDetailViewTest {

  private static final Long TENANT = 1L;
  private static final String POLICY_NO = "POL-D-001";
  private static final String BIZ = "YCBX";
  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimCoverageRepository claimCoverageRepository;
  @Mock LiabilityRepository liabilityRepository;
  @Mock LiabilityFeeRuleRepository feeRuleRepository;
  @Mock CoverageOverrideRepository overrideRepository;
  @Mock CodeMappingRepository codeMappingRepository;
  @Mock MappingMissRepository mappingMissRepository;
  @Mock StdCodeAliasRepository stdCodeAliasRepository;
  @Mock StdCodeSplitRepository stdCodeSplitRepository;
  @Mock ClaimInsurerRepository claimInsurerRepository;
  @Mock InsurerCodeItemRepository insurerCodeItemRepository;
  @Mock FieldBindingRepository fieldBindingRepository;
  @Mock RefHospitalRepository refHospitalRepository;
  @Mock RefDrugRepository refDrugRepository;
  @Mock RefDiseaseRepository refDiseaseRepository;
  @Mock RefFeeCategoryRepository refFeeCategoryRepository;
  @Mock CodeMappingApplicationService codeMappingApplicationService;
  @Mock TenantPort tenantPort;

  @InjectMocks PolicyDetailApplicationService service;

  private ClaimPolicy policy(String biz) {
    return ClaimPolicy.open(
        1L,
        TENANT,
        POLICY_NO,
        "团险",
        "永诚",
        "北京",
        "insure",
        biz,
        "OUTPATIENT",
        "GROUP",
        NOW,
        NOW.plusSeconds(86400),
        0,
        "v1",
        "[\"h1\"]",
        NOW);
  }

  private ClaimPlan plan() {
    return ClaimPlan.draft(5L, TENANT, POLICY_NO, "P1", "基础计划", new BigDecimal("10000"), 0, "v1");
  }

  private AdjLiability liability() {
    return AdjLiability.of(
        10L,
        TENANT,
        "LB-1",
        "v1",
        5L,
        6L,
        "OP-01",
        "OUTPATIENT",
        new BigDecimal("100"),
        BigDecimal.ZERO,
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "",
        "AUTO",
        "H001,H002",
        "D001",
        "INCLUDE",
        "",
        1,
        0,
        "ACTIVE");
  }

  private void stubHealthBase() {
    lenient().when(tenantPort.currentTenantId()).thenReturn(TENANT);
    lenient()
        .when(codeMappingApplicationService.mappingHealth(anyString(), anyString()))
        .thenReturn(
            new CodeMappingApplicationService.MappingHealthView(BIZ, "HOSPITAL", 0, 0, 0d, 0L, 0L));
    lenient()
        .when(claimInsurerRepository.findByInsurerCode(eq(TENANT), eq(BIZ)))
        .thenReturn(
            ClaimInsurer.of(9L, TENANT, BIZ, "永诚财险", "永诚", "DIRECT", "", "", Instant.EPOCH));
    lenient()
        .when(codeMappingRepository.findActiveByInsurerDomain(anyLong(), anyString(), anyString()))
        .thenReturn(List.of());
    lenient()
        .when(mappingMissRepository.countOpen(anyLong(), anyString(), anyString()))
        .thenReturn(0L);
    lenient()
        .when(
            mappingMissRepository.countFirstSeenSince(
                anyLong(), anyString(), anyString(), any(Instant.class)))
        .thenReturn(0L);
  }

  @Test
  void detail_aggregatesHealthTreeAndOverrides() {
    stubHealthBase();
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(policy(BIZ));
    when(claimPlanRepository.findByPolicyNo(POLICY_NO)).thenReturn(List.of(plan()));
    when(liabilityRepository.findActiveByPlanId(5L)).thenReturn(List.of(liability()));
    when(feeRuleRepository.findByLiabilityUuid(TENANT, "LB-1"))
        .thenReturn(
            List.of(
                LiabilityFeeRule.of(
                    20L,
                    TENANT,
                    "LB-1",
                    "F001",
                    "挂号费",
                    new BigDecimal("90"),
                    new BigDecimal("500"),
                    1)));
    ClaimCoverageOverride activeOverride =
        ClaimCoverageOverride.of(
            null,
            TENANT,
            BIZ,
            "LB-1",
            ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES,
            "snap",
            "H003");
    activeOverride.publish(NOW);
    when(overrideRepository.findActiveByBizAndCoverage(TENANT, BIZ, "LB-1"))
        .thenReturn(List.of(activeOverride));

    PolicyDetail detail = service.detail(POLICY_NO);

    assertThat(detail.summary().policyNo()).isEqualTo(POLICY_NO);
    assertThat(detail.summary().bizIdentityCode()).isEqualTo(BIZ);
    assertThat(detail.insurerHealth()).hasSize(MasterDataDomain.values().length);
    assertThat(detail.plans()).hasSize(1);
    var node = detail.plans().get(0).liabilities().get(0);
    assertThat(node.liabilityUuid()).isEqualTo("LB-1");
    assertThat(node.limitHospitalCodes()).isEqualTo("H001,H002");
    assertThat(node.feeRules()).hasSize(1);
    assertThat(node.overrides()).hasSize(1);
    assertThat(node.overrides().get(0).overrideValue()).isEqualTo("H003");
    assertThat(node.overrides().get(0).status()).isEqualTo("ACTIVE");
  }

  @Test
  void detail_blankBizIdentity_skipsHealthLookup() {
    lenient().when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(policy(""));
    when(claimPlanRepository.findByPolicyNo(POLICY_NO)).thenReturn(List.of());
    when(liabilityRepository.findActiveByPlanId(anyLong())).thenReturn(List.of());

    PolicyDetail detail = service.detail(POLICY_NO);

    assertThat(detail.insurerHealth()).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(claimInsurerRepository);
  }

  @Test
  void detail_unknownPolicy_rejected() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(null);

    assertThatThrownBy(() -> service.detail(POLICY_NO))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_POLICY_NOT_FOUND));
  }
}
