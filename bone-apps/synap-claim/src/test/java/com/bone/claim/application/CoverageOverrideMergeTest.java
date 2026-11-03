package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.CoverageOverrideApplicationService.MergePreviewRow;
import com.bone.claim.application.EffectiveConfigViewService.EffectiveConfig;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.ClaimCoverageOverride;
import com.bone.claim.domain.model.liability.LiabilityFeeRule;
import com.bone.claim.domain.repository.ClaimInsurerRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.CoverageOverrideRepository;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
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

/**
 * 两段式覆盖四判据（task 3）：
 *
 * <ul>
 *   <li>继承：无 ACTIVE 覆盖 → 生效 == 实时标准（EffectiveConfigViewService）；
 *   <li>合并：ACTIVE 覆盖逐维替换标准，名单/费用规则互不串扰；
 *   <li>回落：停用后生效 == 标准（disable 只翻状态，不删行）；
 *   <li>PAUSE 兼容：合并从不查保单状态/不改 configStatus——发布确认页才读受影响保单数。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoverageOverrideMergeTest {

  private static final Long TENANT = 1L;
  private static final String BIZ = "YCBX";
  private static final String UUID_ = "LB-UUID-001";

  @Mock CoverageOverrideRepository overrideRepository;
  @Mock LiabilityRepository liabilityRepository;
  @Mock LiabilityFeeRuleRepository feeRuleRepository;
  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock ClaimInsurerRepository claimInsurerRepository;
  @Mock TenantPort tenantPort;

  @InjectMocks CoverageOverrideApplicationService writeService;
  @InjectMocks EffectiveConfigViewService viewService;

  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

  private AdjLiability standardLiability() {
    return AdjLiability.of(
        10L,
        TENANT,
        UUID_,
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

  private LiabilityFeeRule feeRule(String code, String name) {
    return LiabilityFeeRule.of(
        20L, TENANT, UUID_, code, name, new BigDecimal("90"), new BigDecimal("500"), 1);
  }

  private ClaimCoverageOverride activeRow(String field, String value) {
    ClaimCoverageOverride row =
        ClaimCoverageOverride.of(null, TENANT, BIZ, UUID_, field, "snap", value);
    row.publish(NOW);
    return row;
  }

  @Test
  void effectiveConfig_withoutOverrides_equalsStandard() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(overrideRepository.findActiveByBizAndCoverage(TENANT, BIZ, UUID_)).thenReturn(List.of());
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());
    when(feeRuleRepository.findByLiabilityUuid(TENANT, UUID_))
        .thenReturn(List.of(feeRule("F001", "挂号费")));

    EffectiveConfig cfg = viewService.effectiveConfig(BIZ, UUID_);

    assertThat(cfg.limitHospitalCodes()).isEqualTo("H001,H002");
    assertThat(cfg.limitDiagnosisCodes()).isEqualTo("D001");
    assertThat(cfg.feeRules()).hasSize(1);
    assertThat(cfg.feeRules().get(0).payPercent()).isEqualByComparingTo("90");
    // PAUSE 兼容：合并层不读保单状态
    verify(claimPolicyRepository, never()).countActiveByBizIdentity(anyLong(), anyString());
  }

  @Test
  void effectiveConfig_activeOverrideReplacesOnlyItsField() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(overrideRepository.findActiveByBizAndCoverage(TENANT, BIZ, UUID_))
        .thenReturn(List.of(activeRow(ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES, "H003")));
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());
    when(feeRuleRepository.findByLiabilityUuid(TENANT, UUID_)).thenReturn(List.of());

    EffectiveConfig cfg = viewService.effectiveConfig(BIZ, UUID_);

    assertThat(cfg.limitHospitalCodes()).isEqualTo("H003"); // 覆盖生效
    assertThat(cfg.limitDiagnosisCodes()).isEqualTo("D001"); // 标准继承
  }

  @Test
  void effectiveConfig_activeFeeRuleOverride_replacesSameFeeCode() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    ClaimCoverageOverride feeOverride =
        activeRow(
            ClaimCoverageOverride.FEE_RULE_PREFIX + "F001",
            "{\"payPercent\":100,\"limitAmount\":800,\"socialOnly\":0}");
    when(overrideRepository.findActiveByBizAndCoverage(TENANT, BIZ, UUID_))
        .thenReturn(List.of(feeOverride));
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());
    when(feeRuleRepository.findByLiabilityUuid(TENANT, UUID_))
        .thenReturn(List.of(feeRule("F001", "挂号费")));

    EffectiveConfig cfg = viewService.effectiveConfig(BIZ, UUID_);

    assertThat(cfg.feeRules()).hasSize(1);
    assertThat(cfg.feeRules().get(0).payPercent()).isEqualByComparingTo("100");
    assertThat(cfg.feeRules().get(0).limitAmount()).isEqualByComparingTo("800");
    assertThat(cfg.feeRules().get(0).socialOnly()).isZero();
  }

  @Test
  void disable_fallsBackToStandardWithoutDeletingRow() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    ClaimCoverageOverride row =
        ClaimCoverageOverride.of(
            7L,
            TENANT,
            BIZ,
            UUID_,
            ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES,
            "H001,H002",
            "H003");
    row.publish(NOW);
    when(overrideRepository.findById(7L)).thenReturn(row);
    when(overrideRepository.findActiveByBizAndCoverage(TENANT, BIZ, UUID_)).thenReturn(List.of());
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());
    when(feeRuleRepository.findByLiabilityUuid(TENANT, UUID_)).thenReturn(List.of());

    writeService.disable(7L);

    assertThat(row.isActive()).isFalse();
    assertThat(row.getStatus()).isEqualTo("DISABLED");
    verify(overrideRepository).save(row); // 行保留，只置状态
    assertThat(viewService.effectiveConfig(BIZ, UUID_).limitHospitalCodes()).isEqualTo("H001,H002");
  }

  @Test
  void upsert_editingPublishedRow_rejected() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    ClaimInsurerRepository unused = claimInsurerRepository;
    when(claimInsurerRepository.findByInsurerCode(eq(TENANT), eq(BIZ)))
        .thenReturn(
            com.bone.claim.domain.model.mapping.ClaimInsurer.of(
                9L, TENANT, BIZ, "永诚", "永诚", "DIRECT", "", "", Instant.EPOCH));
    ClaimCoverageOverride published =
        ClaimCoverageOverride.of(
            7L, TENANT, BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES, "S", "V");
    published.publish(NOW);
    when(overrideRepository.findRow(
            TENANT, BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES))
        .thenReturn(published);
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());

    assertThatThrownBy(
            () ->
                writeService.upsert(
                    new CoverageOverrideApplicationService.CreateOverrideCommand(
                        BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES, "V2")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("先停用");
  }

  @Test
  void preview_disabledOverride_showsStandardAsEffective() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    ClaimCoverageOverride draft =
        ClaimCoverageOverride.of(
            null,
            TENANT,
            BIZ,
            UUID_,
            ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES,
            "snap",
            "D009");
    when(overrideRepository.findRow(
            TENANT, BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES))
        .thenReturn(null);
    when(overrideRepository.findRow(
            TENANT, BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES))
        .thenReturn(draft);
    when(overrideRepository.findByBizAndCoverage(TENANT, BIZ, UUID_)).thenReturn(List.of(draft));
    when(overrideRepository.findRow(
            TENANT, BIZ, UUID_, ClaimCoverageOverride.FEE_RULE_PREFIX + "F001"))
        .thenReturn(null);
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());
    when(feeRuleRepository.findByLiabilityUuid(TENANT, UUID_))
        .thenReturn(List.of(feeRule("F001", "挂号费")));

    List<MergePreviewRow> rows = writeService.preview(BIZ, UUID_);

    assertThat(rows)
        .extracting(MergePreviewRow::overrideField)
        .contains(ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES);
    MergePreviewRow diagnosis =
        rows.stream()
            .filter(
                r -> r.overrideField().equals(ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES))
            .findFirst()
            .orElseThrow();
    assertThat(diagnosis.effectiveValue()).isEqualTo("D001"); // 未发布不生效
    assertThat(diagnosis.source()).isEqualTo("STANDARD");
    assertThat(diagnosis.overrideValue()).isEqualTo("D009");
  }

  @Test
  void publish_publishesAllDraftsOfTheBizAndCoverage() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    ClaimCoverageOverride draft =
        ClaimCoverageOverride.of(
            null,
            TENANT,
            BIZ,
            UUID_,
            ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES,
            "snap",
            "H003");
    when(overrideRepository.findByBizAndCoverage(TENANT, BIZ, UUID_)).thenReturn(List.of(draft));
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());

    int published = writeService.publish(BIZ, UUID_);

    assertThat(published).isEqualTo(1);
    assertThat(draft.isActive()).isTrue();
    verify(overrideRepository).save(draft);
  }

  @Test
  void upsert_unknownInsurer_rejected() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(claimInsurerRepository.findByInsurerCode(eq(TENANT), anyString())).thenReturn(null);

    assertThatThrownBy(
            () ->
                writeService.upsert(
                    new CoverageOverrideApplicationService.CreateOverrideCommand(
                        "GHOST", UUID_, ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES, "V")))
        .isInstanceOf(com.bone.core.exception.BizException.class);
  }
}
