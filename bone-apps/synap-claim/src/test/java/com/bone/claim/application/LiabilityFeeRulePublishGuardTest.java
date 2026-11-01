package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.LiabilityFeeRule;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 费用类别规则的「存了但不算」闸门（0094 配置面 × 理算内核）。
 *
 * <p><b>为什么必须有这组用例</b>：{@code t_adj_liability_fee_rule} 是可保存的配置，但归集维度只有 {@code
 * FeePools.Bucket}（医保范围内 / 自付二 / 总自费），{@code Adjustment#apply} 也只从 {@code AdjLiability} 装配 {@code
 * LiabilityRule}——规则<b>不参与任何算术</b>。若放行发布，界面显示"手术费 60%"而实赔按责任统一比例， 且 {@code formula_snapshot}
 * 里看不到规则被忽略的痕迹：这是<b>静默错赔</b>，比报错严重得多。
 *
 * <p>两条对照缺一不可：
 *
 * <ul>
 *   <li>有规则 → 拒绝发布，且<b>不落库</b>（否则重试时会带着已发布状态跳过闸门）；
 *   <li>无规则 → 正常发布并开池（证明闸门不是"永远拒绝"，否则用例对实现毫无约束力）。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LiabilityFeeRulePublishGuardTest {

  private static final long TENANT_ID = 1L;
  private static final long LIABILITY_ID = 7001L;
  private static final long PLAN_ID = 1001L;
  private static final String LIABILITY_UUID = "liab-uuid-1";
  private static final String POLICY_NO = "POL-1";

  @Mock LiabilityRepository liabilityRepository;
  @Mock ClaimCoverageRepository claimCoverageRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock LiabilityFeeRuleRepository liabilityFeeRuleRepository;
  @Mock TenantPort tenantPort;
  @Mock DomainEventPublisher domainEventPublisher;
  @Mock QuotaConfigApplicationService quotaConfigApplicationService;

  @InjectMocks LiabilityConfigApplicationService service;

  @BeforeEach
  void fixtures() {
    lenient().when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    // 发布前会校验保单责任规则开关（0094 联动）；这里给「启用中」，让被测闸门是唯一变量。
    ClaimPlan plan =
        ClaimPlan.draft(
            PLAN_ID, TENANT_ID, POLICY_NO, "PLAN-1", "门诊计划", BigDecimal.valueOf(1000), 15, "v1");
    lenient().when(claimPlanRepository.findPlanById(anyLong())).thenReturn(plan);
    ClaimPolicy policy =
        ClaimPolicy.open(
            9001L,
            TENANT_ID,
            POLICY_NO,
            "保单A",
            "保司",
            "分公司",
            "投保方",
            "BIZ-1",
            "OUTPATIENT",
            "GROUP",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-31T00:00:00Z"),
            30,
            "v1",
            "[\"h1\"]",
            Instant.parse("2026-01-01T00:00:00Z"));
    policy.activate("tester");
    lenient().when(claimPolicyRepository.findByPolicyNo(any())).thenReturn(policy);
    lenient().when(liabilityRepository.findById(LIABILITY_ID)).thenReturn(draftLiability());
  }

  @Test
  @DisplayName("存在费用类别规则 → 拒绝发布，且状态不落库（避免重试绕过闸门）")
  void publish_withFeeRules_rejectedAndNotSaved() {
    when(liabilityFeeRuleRepository.findByLiabilityUuid(eq(TENANT_ID), eq(LIABILITY_UUID)))
        .thenReturn(List.of(feeRule("SURGERY", "手术费", "60")));

    assertThatThrownBy(() -> service.publishLiability(LIABILITY_ID))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(ClaimErrorCodes.CLAIM_LIABILITY_FEE_RULE_UNSUPPORTED);

    // 关键断言：被拒时没有 save，库里仍是 DRAFT——否则下次重试时 findActiveByUuid 已能命中，闸门形同虚设。
    verify(liabilityRepository, never()).save(any());
    // 公账开池也不得发生：规则未生效就不该提前占额度池。
    verify(quotaConfigApplicationService, never())
        .initPublicAccountsForLiability(anyLong(), anyString(), any());
  }

  @Test
  @DisplayName("无费用类别规则 → 正常发布并开池（闸门不是永远拒绝）")
  void publish_withoutFeeRules_publishesAndInitializesQuota() {
    when(liabilityFeeRuleRepository.findByLiabilityUuid(eq(TENANT_ID), eq(LIABILITY_UUID)))
        .thenReturn(List.of());

    var view = service.publishLiability(LIABILITY_ID);

    assertThat(view.status()).isEqualTo("ACTIVE");
    verify(liabilityRepository).save(any(AdjLiability.class));
    verify(quotaConfigApplicationService)
        .initPublicAccountsForLiability(eq(PLAN_ID), eq(LIABILITY_UUID), any());
  }

  // ===================== 夹具 =====================

  /** 草稿责任：参数顺序照抄 {@code LiabilityConfigApplicationService#createLiability} 的装配调用。 */
  private static AdjLiability draftLiability() {
    return AdjLiability.of(
        LIABILITY_ID,
        TENANT_ID,
        LIABILITY_UUID,
        "v1",
        PLAN_ID,
        null,
        "L-SURGERY",
        "REIMBURSEMENT",
        new BigDecimal("90"),
        BigDecimal.ZERO,
        new BigDecimal("10000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "NONE",
        "NONE",
        "",
        "",
        "NONE",
        "",
        0,
        0,
        "DRAFT");
  }

  private static LiabilityFeeRule feeRule(String code, String name, String percent) {
    return LiabilityFeeRule.of(
        8001L, TENANT_ID, LIABILITY_UUID, code, name, new BigDecimal(percent), BigDecimal.ZERO, 1);
  }
}
