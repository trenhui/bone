package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimConfigApplicationService.CreatePolicy;
import com.bone.claim.application.ClaimConfigApplicationService.PolicyView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.mapping.ClaimInsurer;
import com.bone.claim.domain.model.mapping.InsurerStatus;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimInsurerRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 保单写入侧的主体引用收紧（{@code assertBizIdentityUsable}）。
 *
 * <p>四类用例钉死 spec 口径：
 *
 * <ul>
 *   <li>不存在主体 → 422（新建/编辑都挡）；——删掉校验调用即转红，是变异杀。
 *   <li>DISABLED/TERMINATED 主体 → 422（停用保司冻结映射面不能被新保单绕开）。
 *   <li>空主体 → 放行且<b>不查</b>主体仓储（存量自由文本迁移过来的保单不受影响）。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ClaimConfigBizIdentityGuardTest {

  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimCoverageRepository claimCoverageRepository;
  @Mock ClaimInsurerRepository claimInsurerRepository;
  @Mock TenantPort tenantPort;
  @Mock DomainEventPublisher domainEventPublisher;

  @InjectMocks ClaimConfigApplicationService service;

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant T1 = Instant.parse("2026-12-31T00:00:00Z");
  private static final String POLICY_NO = "POL-BIZ-001";

  private CreatePolicy createCmd(String bizIdentityCode, String insurerPolicyNo) {
    return new CreatePolicy(
        POLICY_NO,
        "团险",
        "永诚财险",
        "北京分公司",
        "insure",
        bizIdentityCode,
        "OUTPATIENT",
        "GROUP",
        T0,
        T1,
        0,
        "v1",
        "[\"h1\"]",
        null,
        null,
        insurerPolicyNo);
  }

  private ClaimInsurer insurerWithStatus(InsurerStatus status) {
    ClaimInsurer insurer = ClaimInsurer.of(9L, 1L, "YCBX", "永诚财险", "永诚", "DIRECT", "", "", T0);
    insurer.changeStatus(status, T0);
    return insurer;
  }

  private void stubLookup(ClaimInsurer insurer) {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(null);
    when(claimInsurerRepository.findByInsurerCode(1L, "YCBX")).thenReturn(insurer);
    when(tenantPort.currentTenantId()).thenReturn(1L);
  }

  @Test
  void createPolicy_withUnknownInsurer_rejected() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(null);
    when(claimInsurerRepository.findByInsurerCode(1L, "YCBX")).thenReturn(null);
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createPolicy(createCmd("YCBX", null)))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_BIZ_IDENTITY_INVALID));
  }

  @Test
  void createPolicy_withDisabledInsurer_rejected() {
    stubLookup(insurerWithStatus(InsurerStatus.DISABLED));

    assertThatThrownBy(() -> service.createPolicy(createCmd("YCBX", null)))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_BIZ_IDENTITY_INVALID));
  }

  @Test
  void createPolicy_withTerminatedInsurer_rejected() {
    stubLookup(insurerWithStatus(InsurerStatus.TERMINATED));

    assertThatThrownBy(() -> service.createPolicy(createCmd("YCBX", null)))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("停用或终止");
  }

  @Test
  void createPolicy_withBlankIdentity_skipsInsurerLookup() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(null);
    when(tenantPort.currentTenantId()).thenReturn(1L);

    PolicyView view = service.createPolicy(createCmd("  ", null));
    assertThat(view.bizIdentityCode()).isBlank();
    verifyNoInteractions(claimInsurerRepository);
  }

  @Test
  void createPolicy_withActiveInsurer_accepted() {
    stubLookup(insurerWithStatus(InsurerStatus.ACTIVE));

    PolicyView view = service.createPolicy(createCmd("YCBX", null));
    assertThat(view.bizIdentityCode()).isEqualTo("YCBX");
  }

  @Test
  void createPolicy_capturesInsurerPolicyNo() {
    stubLookup(insurerWithStatus(InsurerStatus.ACTIVE));

    var view = service.createPolicy(createCmd("YCBX", "INS-POL-001"));
    assertThat(view.insurerPolicyNo()).isEqualTo("INS-POL-001");
  }

  @Test
  void updatePolicy_withUnknownInsurer_rejected() {
    ClaimPolicy policy =
        ClaimPolicy.open(
            1L,
            1L,
            POLICY_NO,
            "旧名",
            "ins",
            "br",
            "insure",
            "",
            "OUTPATIENT",
            "GROUP",
            T0,
            T1,
            0,
            "v1",
            "[\"h1\"]",
            T0);
    try {
      // open 出来的聚合 version 为 null（SDK 落库时置值），守护比较要求非空，反射置值绕开
      java.lang.reflect.Field f = ClaimPolicy.class.getDeclaredField("version");
      f.setAccessible(true);
      f.set(policy, 3L);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(policy);
    when(claimInsurerRepository.findByInsurerCode(anyLong(), anyString())).thenReturn(null);
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.updatePolicy(POLICY_NO, editCmd("YCBX", null)))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_BIZ_IDENTITY_INVALID));
  }

  private ClaimConfigApplicationService.UpdatePolicy editCmd(String bizIdentityCode, Long version) {
    return new ClaimConfigApplicationService.UpdatePolicy(
        "新名",
        "ins",
        "br",
        "insure",
        bizIdentityCode,
        "OUTPATIENT",
        "GROUP",
        T0,
        T1,
        0,
        "v1",
        "[\"h1\"]",
        null,
        null,
        version);
  }
}
