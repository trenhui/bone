package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.ClaimCoverageOverride;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 覆盖三态判别力：同步默认不启用、已有专属（含 MODIFIED）不随标准变。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoverageOverrideOriginTest {

  private static final Long TENANT = 1L;
  private static final String BIZ = "YCBX";
  private static final String UUID_ = "LB-UUID-001";
  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

  @Mock CoverageOverrideRepository overrideRepository;
  @Mock LiabilityRepository liabilityRepository;
  @Mock LiabilityFeeRuleRepository feeRuleRepository;
  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock ClaimInsurerRepository claimInsurerRepository;
  @Mock TenantPort tenantPort;

  @InjectMocks CoverageOverrideApplicationService service;

  private AdjLiability standardLiability() {
    return AdjLiability.of(
        10L,
        TENANT,
        UUID_,
        "v2",
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

  @Test
  void applySync_defaultsToDisabledAndStampsVersion_withoutEnabling() {
    ClaimCoverageOverride row =
        ClaimCoverageOverride.of(
            null,
            TENANT,
            BIZ,
            UUID_,
            ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES,
            "H001,H002",
            "H001,H002");

    assertThat(row.getOrigin()).isEqualTo(ClaimCoverageOverride.ORIGIN_EXCLUSIVE);
    assertThat(row.getStatus()).isEqualTo(ClaimCoverageOverride.STATUS_DISABLED);

    row.applySync("v2", NOW);

    assertThat(row.getLastSyncVersion()).isEqualTo("v2");
    assertThat(row.getStatus()).isEqualTo(ClaimCoverageOverride.STATUS_DISABLED);
    assertThat(row.isActive()).isFalse();
    assertThat(row.getOrigin()).isEqualTo(ClaimCoverageOverride.ORIGIN_EXCLUSIVE);
  }

  @Test
  void markModified_switchesOriginAndStandardSyncDoesNotTouchIt() {
    ClaimCoverageOverride row =
        ClaimCoverageOverride.of(
            7L,
            TENANT,
            BIZ,
            UUID_,
            ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES,
            "H001,H002",
            "H001");
    row.publish(NOW);
    row.markModified(NOW);

    assertThat(row.getOrigin()).isEqualTo(ClaimCoverageOverride.ORIGIN_MODIFIED);
    String valueBefore = row.getOverrideValue();

    // 模拟标准升级同步：已有专属（MODIFIED）行被 findRow 命中 ⇒ 跳过，不被标准覆盖
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());
    when(feeRuleRepository.findByLiabilityUuid(TENANT, UUID_)).thenReturn(List.of());
    when(overrideRepository.findRow(
            TENANT, BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES))
        .thenReturn(row);
    when(overrideRepository.findRow(
            TENANT, BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES))
        .thenReturn(null);

    service.syncStandardFields(BIZ, UUID_);

    assertThat(row.getOrigin()).isEqualTo(ClaimCoverageOverride.ORIGIN_MODIFIED);
    assertThat(row.getOverrideValue()).isEqualTo(valueBefore);
    verify(overrideRepository, never()).save(row);
  }

  @Test
  void syncStandardFields_insertsMissingFieldsAsDisabledExclusiveRows() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(liabilityRepository.findActiveByUuid(UUID_)).thenReturn(standardLiability());
    when(feeRuleRepository.findByLiabilityUuid(TENANT, UUID_)).thenReturn(List.of());
    when(overrideRepository.findRow(
            TENANT, BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES))
        .thenReturn(null);
    when(overrideRepository.findRow(
            TENANT, BIZ, UUID_, ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES))
        .thenReturn(null);

    int added = service.syncStandardFields(BIZ, UUID_);

    assertThat(added).isEqualTo(2); // 名单两维，费用规则为空
    ArgumentCaptor<ClaimCoverageOverride> captor =
        ArgumentCaptor.forClass(ClaimCoverageOverride.class);
    verify(overrideRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .allSatisfy(
            saved -> {
              assertThat(saved.getOrigin()).isEqualTo(ClaimCoverageOverride.ORIGIN_EXCLUSIVE);
              assertThat(saved.getStatus()).isEqualTo(ClaimCoverageOverride.STATUS_DISABLED);
              assertThat(saved.isActive()).isFalse();
              assertThat(saved.getLastSyncVersion()).isEqualTo("v2");
            });
  }
}
