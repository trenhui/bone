package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.settlement.AccountType;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自动通过结论消费者（A3 决策 → A1 结案 → A7 建结算单的编排）。
 *
 * <p><b>补的是什么洞</b>：决策图产出 {@code AUTO_APPROVE} 后，此前没有任何消费者——案件停在「已初审（12）」，既不出金也不 转人工；若人工改调 {@code
 * /claims/{id}/auto-approve}，又只把案件推进到 {@code FINISHED} 而不建结算单，结果是 「已结案但资金链无单」——对账能查出来，但当晚才发现。本类把「结论
 * → 结案 → 建单（软占额度）」串成一次事务。
 *
 * <p><b>为什么默认关闭（fail-closed）</b>：{@code AUTO_APPROVE} 意味着<b>不经人工直接放款</b>，属资金策略变更，须产品/架构 签核。开关
 * {@code bone.claim.auto-approve.enabled=false} 时本类只做判定并返回原因，不触碰任何聚合——「代码就绪、
 * 策略待签核」两个状态因此可分辨，而不是靠注释宣称。
 *
 * <p><b>金额口径</b>：上限校验用<b>理算结果</b>（{@link IndemnityApplicationService#latest}），不用 {@code
 * t_claim_case.approved_amount}——后者是决策时的快照，与结算金额（理算权威）可能不一致，用快照限额等于给「先按小金额 过限额、再按大理算出金」留口子。
 *
 * <p><b>为何两个入口</b>：{@link #applyAutoDecision} 是<b>机器</b>触发的自动消费（受开关与单笔上限约束）； {@link
 * #approveAndSettle} 是<b>人</b>显式点击的快捷赔付（操作人可追责，不受开关约束）。二者共用同一段 「结案 + 建单」编排，避免两条路径的出金行为漂移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoApprovalApplicationService {

  /** 系统操作人标识（自动路径的唯一可追责主体；审阅日志按此值筛出全部机器动作）。 */
  public static final String SYSTEM_OPERATOR = "SYSTEM";

  /** 首次占额周期序号（§7.5；退款/重占时递增）。 */
  private static final int FIRST_ATTEMPT = 1;

  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final SettlementApplicationService settlementApplicationService;
  private final IndemnityApplicationService indemnityApplicationService;
  private final DecisionExecutionApplicationService decisionExecutionApplicationService;

  /** 自动放款总开关（默认关闭：策略须签核）。 */
  @Value("${bone.claim.auto-approve.enabled:false}")
  private boolean enabled;

  /** 自动放款单笔上限（元）；超出即保持人工核赔，不自动出金。 */
  @Value("${bone.claim.auto-approve.max-amount:1000}")
  private BigDecimal maxAmount;

  /** 消费结果（{@code applied=false} 时 {@code reason} 必为受控原因码，便于聚合「为什么没自动放款」）。 */
  public record AutoApprovalOutcome(
      boolean applied, String reason, String settlementNo, BigDecimal amount) {

    public static AutoApprovalOutcome skipped(String reason) {
      return new AutoApprovalOutcome(false, reason, null, null);
    }
  }

  /**
   * 机器消费决策结论（决策图产出 {@code AUTO_APPROVE} 后由执行器调用）。
   *
   * <p>判定顺序即「先便宜后昂贵」：无执行 → 非自动通过 → 开关关闭 → 已结案 → 超限额，全部不碰聚合；只有全通过才进入 「结案 + 建单」。任一档拒绝都返回受控原因码，绝不静默。
   */
  @Transactional
  public AutoApprovalOutcome applyAutoDecision(long claimId) {
    if (!enabled) {
      return AutoApprovalOutcome.skipped("AUTO_APPROVE_DISABLED");
    }
    var execution = decisionExecutionApplicationService.findLatest(claimId);
    if (execution == null) {
      return AutoApprovalOutcome.skipped("NO_DECISION");
    }
    if (!"AUTO_APPROVE".equals(execution.getDecisionResult())) {
      return AutoApprovalOutcome.skipped("NOT_AUTO_APPROVE:" + execution.getDecisionResult());
    }
    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    if (claim == null) {
      return AutoApprovalOutcome.skipped("CLAIM_NOT_FOUND");
    }
    if (claim.finished()) {
      return AutoApprovalOutcome.skipped("ALREADY_SETTLED");
    }
    BigDecimal amount = adjustmentAmount(claimId);
    if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
      log.info("[自动放款] 超单笔上限转人工: claimId={}, amount={}, cap={}", claimId, amount, maxAmount);
      return AutoApprovalOutcome.skipped("CAP_EXCEEDED");
    }
    return approveAndSettleInternal(claimId, SYSTEM_OPERATOR, amount);
  }

  /**
   * 人工显式自动通过（快捷赔付 R-FAST-01；操作人可追责，不受开关/上限约束）。
   *
   * <p>与 {@link #applyAutoDecision} 共用「结案 + 建单」段：任何走到 {@code FINISHED} 的路径都必须有结算单，
   * 否则就是「已结案未出金」的静默欠账。
   */
  @Transactional
  public AutoApprovalOutcome approveAndSettle(long claimId, String operatorId) {
    BigDecimal amount = adjustmentAmount(claimId);
    return approveAndSettleInternal(claimId, operatorId, amount);
  }

  // ===================== 内部 =====================

  private AutoApprovalOutcome approveAndSettleInternal(
      long claimId, String operatorId, BigDecimal amount) {
    // 结案（12 → 50）：领域层校验「已初审 + 核定金额已回填」，不满足即抛，本事务回滚（不会出现「结了案没建单」）
    claimCaseApplicationService.autoApprove(claimId, operatorId);
    SettlementApplicationService.SettlementResult settlement =
        settlementApplicationService.create(
            claimId, FIRST_ATTEMPT, AccountType.NO_ACCOUNT, operatorId);
    log.info(
        "[自动放款] 结案并建结算单: claimId={}, amount={}, settlementNo={}, operator={}",
        claimId,
        amount,
        settlement.settlementNo(),
        operatorId);
    return new AutoApprovalOutcome(true, "APPLIED", settlement.settlementNo(), settlement.amount());
  }

  /** 理算结果金额（资金权威口径；缺失即 404，自动放款绝不允许无依据出金）。 */
  private BigDecimal adjustmentAmount(long claimId) {
    IndemnityApplicationService.AdjustmentResult adjustment =
        indemnityApplicationService.latest(claimId);
    if (adjustment == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, claimId);
    }
    return adjustment.totalAmount();
  }
}
