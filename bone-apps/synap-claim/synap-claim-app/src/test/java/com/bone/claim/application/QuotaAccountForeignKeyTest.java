package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bone.claim.application.QuotaConfigApplicationService.OpenQuota;
import com.bone.claim.application.QuotaConfigApplicationService.QuotaAccountView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.QuotaChangeLogRepository;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 开户（{@code openAccount}）的外键完整性守护：额度账户必须挂在**真实存在且属于本保单**的计划上。
 *
 * <p><b>为什么必须守</b>：额度账户是「某被保人在某保单某计划下的额度」。若 {@code plan_id} 指向不存在的计划，
 * 就会造出一个<b>孤儿账户</b>——它自身看着完全正常（额度、占用、守恒全都对），只有到"按计划查账户"或 理算时才会发现计划为 null，排查成本极高，且没有任何一条错误信息指向真因。
 *
 * <p><b>为什么这条洞真实存在过</b>：雪花 ID 是 18~19 位，<b>超出 JS Number 上限 2^53</b>。 调用方只要让 ID 经过一次 {@code
 * Number()} 往返（前端类型声明写成 {@code number} 时极易发生）， 末位就被静默截断，后端照单全收 ⇒ 账户挂到了「一个不存在的计划」上且**全程无任何报错**。
 * 所以这不是"洁癖"，而是一条真实可达的错误路径。
 *
 * <p><b>用例设计</b>：
 *
 * <ul>
 *   <li>{@link #unknownPlan_isRejected()}——计划不存在 ⇒ 拒绝（删掉守护即转红，是真正的变异杀）；
 *   <li>{@link #planOfOtherPolicy_isRejected()}——计划存在但属于别的保单 ⇒ 同样拒绝（防"跨保单挂额度"）；
 *   <li>{@link #validPlan_opensAccount()}——计划存在且归属正确 ⇒ 正常开户并留痕。
 * </ul>
 *
 * <p><b>幂等语义不受影响</b>：已存在同键账户时直接返回既有账户（运营重复点"初始化"是正常操作），
 * 该路径<b>不</b>做计划存在性校验——否则历史数据（计划已下线/删除）会让运营再也点不动"初始化"。
 */
@ExtendWith(MockitoExtension.class)
class QuotaAccountForeignKeyTest {

  private static final long PLAN_ID = 880000000000000001L;
  private static final String POLICY_NO = "POL-FK-001";

  @Mock QuotaAccountRepository quotaAccountRepository;
  @Mock QuotaChangeLogRepository quotaChangeLogRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock TenantPort tenantPort;

  @InjectMocks QuotaConfigApplicationService service;

  private OpenQuota cmd() {
    return new OpenQuota(
        "hash-fk-001",
        POLICY_NO,
        PLAN_ID,
        2026,
        "PERSONAL",
        "",
        new BigDecimal("20000"),
        "op",
        "op",
        "开户");
  }

  private ClaimPlan planOf(String policyNo) {
    return ClaimPlan.draft(
        PLAN_ID, 1L, policyNo, "PLAN-FK-001", "计划", new BigDecimal("50000"), 0, "v1");
  }

  @Test
  void unknownPlan_isRejected() {
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(null);
    when(claimPlanRepository.findPlanById(PLAN_ID)).thenReturn(null);

    assertThatThrownBy(() -> service.openAccount(cmd()))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_PLAN_NOT_FOUND));
  }

  @Test
  void planOfOtherPolicy_isRejected() {
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(null);
    when(claimPlanRepository.findPlanById(PLAN_ID)).thenReturn(planOf("POL-OTHER"));

    assertThatThrownBy(() -> service.openAccount(cmd()))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_PLAN_NOT_FOUND));
  }

  @Test
  void validPlan_opensAccount() {
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(null);
    when(claimPlanRepository.findPlanById(PLAN_ID)).thenReturn(planOf(POLICY_NO));
    when(tenantPort.currentTenantId()).thenReturn(1L);

    QuotaAccountView view = service.openAccount(cmd());

    assertThat(view.policyNo()).isEqualTo(POLICY_NO);
    assertThat(view.planId()).isEqualTo(String.valueOf(PLAN_ID));
    assertThat(view.initial()).isEqualByComparingTo("20000");
  }
}
