package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimConfigApplicationService.PolicyView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 配置面编辑的乐观锁冲突检测（{@code assertEditableVersion}）。
 *
 * <p><b>为什么是这三类用例</b>：
 *
 * <ul>
 *   <li>{@link #staleVersion_throwsConcurrentConflict()}——提交版本落后于当前版本，必须抛出 {@code
 *       CLAIM_CONCURRENT_CONFLICT}（409）；删掉 {@code assertEditableVersion} 这一行即转红，是真正的变异杀。
 *   <li>{@link #matchingVersion_appliesEdit()}——版本一致，编辑正常落盘。
 *   <li>{@link #nullSubmittedVersion_bypassesGuard()}——不带版本的非前端调用方（如脚本/内部调用）仍被放行，不破坏既有契约。
 * </ul>
 *
 * <p><b>版本种子的取法</b>：{@link ClaimPolicy#open} 出来的聚合 {@code version} 为 null（由 SDK 在首次落库时置值）， 而守护比较要求
 * {@code currentVersion} 非空，故用反射把被测实体的版本置成 5，再让提交方带上 4（过期）或 5（一致）。
 */
@ExtendWith(MockitoExtension.class)
class ClaimConfigOptimisticLockTest {

  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimCoverageRepository claimCoverageRepository;
  @Mock TenantPort tenantPort;
  @Mock DomainEventPublisher domainEventPublisher;

  @InjectMocks ClaimConfigApplicationService service;

  private static final String POLICY_NO = "POL-OPTLOCK-001";
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant T1 = Instant.parse("2026-12-31T00:00:00Z");

  private ClaimPolicy savedPolicy(long version) {
    ClaimPolicy p =
        ClaimPolicy.open(
            1L,
            1L,
            POLICY_NO,
            "旧名称",
            "ins",
            "br",
            "insure",
            "biz",
            "OUTPATIENT",
            "GROUP",
            T0,
            T1,
            0,
            "v1",
            "[\"h1\"]",
            T0);
    try {
      Field f = ClaimPolicy.class.getDeclaredField("version");
      f.setAccessible(true);
      f.set(p, version);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return p;
  }

  private ClaimConfigApplicationService.UpdatePolicy editCmd(Long submittedVersion) {
    return new ClaimConfigApplicationService.UpdatePolicy(
        "新名称",
        "ins",
        "br",
        "insure",
        "biz",
        "OUTPATIENT",
        "GROUP",
        T0,
        T1,
        0,
        "v1",
        "[\"h1\"]",
        null,
        null,
        submittedVersion);
  }

  @Test
  void staleVersion_throwsConcurrentConflict() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(savedPolicy(5L));

    assertThatThrownBy(() -> service.updatePolicy(POLICY_NO, editCmd(4L)))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_CONCURRENT_CONFLICT));
  }

  @Test
  void matchingVersion_appliesEdit() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(savedPolicy(5L));

    PolicyView view = service.updatePolicy(POLICY_NO, editCmd(5L));
    assertThat(view.policyName()).isEqualTo("新名称");
    assertThat(view.version()).isEqualTo(5L);
  }

  @Test
  void nullSubmittedVersion_bypassesGuard() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(savedPolicy(5L));

    PolicyView view = service.updatePolicy(POLICY_NO, editCmd(null));
    assertThat(view.policyName()).isEqualTo("新名称");
  }
}
