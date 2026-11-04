package com.bone.claim.infrastructure.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.PolicyQueryPort.PolicySnapshot;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 保单快照适配器单测：只钉"实体 → 端口快照"的字段搬运，不重复测应用层的解析/消歧判定。
 *
 * <p>为什么这层也要测：{@code insurerPolicyNo} 若在适配器里被丢掉，应用层的双号交叉校验就永远看不见它——
 * 校验代码还在、测试也绿，但实际退化成"永远不校验"（静默失效）。这类缺口只有在本层断言字段确实被搬运才能发现。
 */
@ExtendWith(MockitoExtension.class)
class PolicyQueryPortAdapterTest {

  private static final Long PLAN_ID = 7L;

  @Mock private ClaimPolicyRepository claimPolicyRepository;
  @InjectMocks private PolicyQueryPortAdapter adapter;

  @Test
  @DisplayName("按保司号查：多行全部映射（含保司号与被保人清单），不在这里替调用方取第一条")
  void findActivePoliciesByInsurerNoMapsEveryRow() {
    when(claimPolicyRepository.findUsablePoliciesByInsurerNo("INS-001"))
        .thenReturn(
            List.of(
                policy(1L, "EG-A", "INS-001", "[\"h1\",\"h2\"]"),
                policy(2L, "EG-B", "INS-001", "[\"h3\"]")));

    List<PolicySnapshot> snapshots = adapter.findActivePoliciesByInsurerNo("INS-001", PLAN_ID);

    assertThat(snapshots).as("保司号不唯一，命中多少就必须返回多少").hasSize(2);
    assertThat(snapshots)
        .extracting(PolicySnapshot::policyNo)
        .as("顺序沿用仓储排序（effective_start DESC, id DESC），供日志/人工核查复现")
        .containsExactly("EG-A", "EG-B");
    assertThat(snapshots.get(0).insurerPolicyNo()).isEqualTo("INS-001");
    assertThat(snapshots.get(0).insuredIdHashes()).containsExactly("h1", "h2");
    assertThat(snapshots.get(1).covers("h3")).isTrue();
    assertThat(snapshots.get(1).covers("h1")).as("承保名单不跨保单串味").isFalse();
  }

  @Test
  @DisplayName("按保司号查：仓储无命中 → 空列表（不是 null，调用方无需判空）")
  void findActivePoliciesByInsurerNoReturnsEmptyList() {
    when(claimPolicyRepository.findUsablePoliciesByInsurerNo("INS-404")).thenReturn(List.of());

    assertThat(adapter.findActivePoliciesByInsurerNo("INS-404", PLAN_ID)).isEmpty();
  }

  @Test
  @DisplayName("按平台号查：快照同样带出保司号（否则双号交叉校验永远拿不到对照值）")
  void findActivePolicyCarriesInsurerPolicyNo() {
    when(claimPolicyRepository.findUsablePolicy("EG-A"))
        .thenReturn(policy(1L, "EG-A", "INS-001", "[\"h1\"]"));

    Optional<PolicySnapshot> snapshot = adapter.findActivePolicy("EG-A", PLAN_ID);

    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().insurerPolicyNo()).isEqualTo("INS-001");
  }

  private static ClaimPolicy policy(
      long id, String policyNo, String insurerPolicyNo, String insured) {
    ClaimPolicy policy =
        ClaimPolicy.open(
            id,
            0L,
            policyNo,
            "测试保单",
            "测试保司",
            "分公司",
            "投保公司",
            "BIZ-1",
            "OUTPATIENT",
            "GROUP",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-31T00:00:00Z"),
            30,
            "v1",
            insured,
            Instant.parse("2026-01-01T00:00:00Z"));
    policy.assignInsurerPolicyNo(insurerPolicyNo);
    return policy;
  }
}
