package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimConfigApplicationService.PolicyView;
import com.bone.claim.application.ClaimConfigApplicationService.UpdatePolicy;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimInsurerRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 保司侧保单号（§16-2）的可维护性：编辑保单时必须能改，但不能被"没传"抹掉。
 *
 * <p>三条用例钉死三种入参语义：
 *
 * <ul>
 *   <li><b>传值</b> → 覆盖（含首尾空白归一）；——删掉赋值即转红。
 *   <li><b>不传（null）</b> → 保留原值；——若改成无条件 {@code assignInsurerPolicyNo}（它把 null 归一成空串），本条转红。
 *       这是真实故障形态：上线后旧前端/脚本编辑一次保单，存量保司号被静默清零，且列表里看不出来。
 *   <li><b>传空串</b> → 显式清空；——若把判断收紧成 {@code !isBlank()}，本条转红。清空是合法业务表达（保司号录错要能撤掉）， 不能因为"怕抹掉"就堵死。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ClaimPolicyInsurerNoMaintenanceTest {

  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimCoverageRepository claimCoverageRepository;
  @Mock ClaimInsurerRepository claimInsurerRepository;
  @Mock TenantPort tenantPort;
  @Mock DomainEventPublisher domainEventPublisher;

  @InjectMocks ClaimConfigApplicationService service;

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant T1 = Instant.parse("2026-12-31T00:00:00Z");
  private static final String POLICY_NO = "POL-INS-001";
  private static final String EXISTING_INSURER_NO = "INS-POL-OLD";

  /** 存量保单：已带保司侧保单号，模拟 §16-2 上线后已录入的数据。 */
  private ClaimPolicy existingPolicy() {
    ClaimPolicy policy =
        ClaimPolicy.open(
            1L,
            1L,
            POLICY_NO,
            "团险",
            "永诚财险",
            "北京分公司",
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
    policy.assignInsurerPolicyNo(EXISTING_INSURER_NO);
    try {
      // open 出来的聚合 version 为 null（SDK 落库时置值），updatePolicy 的乐观锁校验要求非空，反射置值绕开
      java.lang.reflect.Field f = ClaimPolicy.class.getDeclaredField("version");
      f.setAccessible(true);
      f.set(policy, 3L);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return policy;
  }

  private UpdatePolicy updateCmd(String insurerPolicyNo) {
    return new UpdatePolicy(
        "团险",
        "永诚财险",
        "北京分公司",
        "insure",
        "",
        "OUTPATIENT",
        "GROUP",
        T0,
        T1,
        0,
        "v1",
        "[\"h1\"]",
        null,
        null,
        insurerPolicyNo,
        null);
  }

  @Test
  void updatePolicy_withExplicitValue_overwrites() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(existingPolicy());

    PolicyView view = service.updatePolicy(POLICY_NO, updateCmd("  INS-POL-NEW  "));

    assertThat(view.insurerPolicyNo()).isEqualTo("INS-POL-NEW");
  }

  @Test
  void updatePolicy_withNull_keepsExistingValue() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(existingPolicy());

    PolicyView view = service.updatePolicy(POLICY_NO, updateCmd(null));

    assertThat(view.insurerPolicyNo()).isEqualTo(EXISTING_INSURER_NO);
  }

  @Test
  void updatePolicy_withEmptyString_clearsValue() {
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(existingPolicy());

    PolicyView view = service.updatePolicy(POLICY_NO, updateCmd(""));

    assertThat(view.insurerPolicyNo()).isEmpty();
  }
}
