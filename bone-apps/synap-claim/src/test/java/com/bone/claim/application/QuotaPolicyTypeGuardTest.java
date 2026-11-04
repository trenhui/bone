package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.QuotaChangeLogRepository;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 保单类型（团体 / 个人）在额度池初始化上的业务判定。
 *
 * <p><b>判定内容</b>：个人保单的业务定义是「自然人投保、单人单账户」，所以它名下未减人的个人额度账户最多一个。
 * 多于一个不是"要多开几个责任池"，而是<b>保单类型或被保人名单已经错了</b>——继续按人开池，责任额度会被开到
 * 不存在的人头上（额度虚增、理赔对不上人），属资金级错误，事后只能靠流水对账回溯。
 *
 * <p><b>为什么这条判定挂在"责任发布"这个动作上</b>：责任额度池的唯一自动开立入口就是发布 （{@code
 * LiabilityConfigApplicationService#publishLiability} 的联动），把闸门放在这里，等于把错误挡在
 * <b>额度真正被建出来之前</b>；放在开户接口上只能挡住后来者，挡不住已经开出去的一批。
 *
 * <p><b>正向对照</b>：{@link #groupPolicyWithTwoAccounts_stillInitializes()} 与 {@link
 * #individualPolicyWithSingleAccount_stillInitializes()} 证明这条闸门不是"一律拒绝"——
 * 团体保单多被保人、个人保单单人都是正常形态，必须放行。
 */
@ExtendWith(MockitoExtension.class)
class QuotaPolicyTypeGuardTest {

  private static final long PLAN_ID = 1001L;
  private static final String POLICY_NO = "POL-TYPE-1";

  @Mock QuotaAccountRepository quotaAccountRepository;
  @Mock QuotaChangeLogRepository quotaChangeLogRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock TenantPort tenantPort;

  @InjectMocks QuotaConfigApplicationService service;

  @Test
  void individualPolicyWithTwoAccounts_isRejected() {
    when(claimPlanRepository.findPlanById(PLAN_ID)).thenReturn(plan());
    when(quotaAccountRepository.findAccountsByPolicyNo(POLICY_NO))
        .thenReturn(List.of(personal("hash-1"), personal("hash-2")));
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(policy("INDIVIDUAL"));

    assertThatThrownBy(
            () ->
                service.initPublicAccountsForLiability(
                    PLAN_ID, "liability-uuid", null, new BigDecimal("5000")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_POLICY_TYPE_CONFLICT))
        .hasMessageContaining("单人单账户");

    // 闸门必须在"开池"之前：一个账户都不许落库
    verify(quotaAccountRepository, never()).save(any());
  }

  @Test
  void groupPolicyWithTwoAccounts_stillInitializes() {
    when(claimPlanRepository.findPlanById(PLAN_ID)).thenReturn(plan());
    when(quotaAccountRepository.findAccountsByPolicyNo(POLICY_NO))
        .thenReturn(List.of(personal("hash-1"), personal("hash-2")));
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(policy("GROUP"));
    // 已存在的公账账户 ⇒ openAccount 走幂等分支直接返回，本用例只关心"闸门放行"
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(existingCapAccount());

    assertThat(
            service.initPublicAccountsForLiability(PLAN_ID, "liability-uuid", null, BigDecimal.TEN))
        .isEqualTo(2);
  }

  @Test
  void individualPolicyWithSingleAccount_stillInitializes() {
    when(claimPlanRepository.findPlanById(PLAN_ID)).thenReturn(plan());
    when(quotaAccountRepository.findAccountsByPolicyNo(POLICY_NO))
        .thenReturn(List.of(personal("hash-1")));
    // 闸门在"账户数 ≤ 1"时直接放行、不查保单主数据（省一次查询，也避免保单缺失误伤单人保单）
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(existingCapAccount());

    assertThat(
            service.initPublicAccountsForLiability(PLAN_ID, "liability-uuid", null, BigDecimal.TEN))
        .isEqualTo(1);
    verify(claimPolicyRepository, never()).findByPolicyNo(anyString());
  }

  /** 保单主数据缺失（配置面存量/测试数据）时放行：本闸门只挡"确定矛盾"，不承担保单存在性校验。 */
  @Test
  void missingPolicyMasterData_doesNotBlockInitialization() {
    when(claimPlanRepository.findPlanById(PLAN_ID)).thenReturn(plan());
    when(quotaAccountRepository.findAccountsByPolicyNo(POLICY_NO))
        .thenReturn(List.of(personal("hash-1"), personal("hash-2")));
    when(claimPolicyRepository.findByPolicyNo(POLICY_NO)).thenReturn(null);
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(existingCapAccount());

    assertThatCode(
            () ->
                service.initPublicAccountsForLiability(
                    PLAN_ID, "liability-uuid", null, BigDecimal.TEN))
        .doesNotThrowAnyException();
  }

  // ===================== 夹具 =====================

  private static ClaimPlan plan() {
    return ClaimPlan.draft(PLAN_ID, 1L, POLICY_NO, "P1", "计划一", BigDecimal.ZERO, 0, "v1");
  }

  private static ClaimPolicy policy(String policyType) {
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = Instant.parse("2026-12-31T23:59:59Z");
    return ClaimPolicy.open(
        1L,
        1L,
        POLICY_NO,
        "保单",
        "保司",
        "分公司",
        "投保单位",
        "BIZ-1",
        "OUTPATIENT",
        policyType,
        start,
        end,
        0,
        "v1",
        "[\"hash-1\",\"hash-2\"]",
        start);
  }

  private static QuotaAccount personal(String insuredIdHash) {
    return QuotaAccount.open(
        1L, 1L, insuredIdHash, POLICY_NO, PLAN_ID, 2026, "PERSONAL", "", new BigDecimal("1000"));
  }

  private static QuotaAccount existingCapAccount() {
    return QuotaAccount.open(
        9L,
        1L,
        "hash-1",
        POLICY_NO,
        PLAN_ID,
        2026,
        "LIABILITY_CAP",
        "liability-uuid",
        new BigDecimal("5000"));
  }
}
