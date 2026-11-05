package com.bone.claim.application;

import com.bone.claim.application.AutoApprovalApplicationService.AutoApprovalOutcome;
import com.bone.claim.application.DecisionGraphExecutor.DecisionExecutionResult;
import com.bone.claim.application.DecisionGraphExecutor.DecisionVersions;
import com.bone.claim.application.IndemnityApplicationService.AdjustmentResult;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.decision.DecisionResult;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 决策执行用例入口（执行决策图 → 消费结论 → 案件落到该去的地方）。
 *
 * <p><b>为什么不让 {@link DecisionGraphExecutor} 自己消费结论</b>：执行器承载的是「证据 → 解析 → 核责 → 理算 → 决策」
 * 的确定性链路，它的可测试性依赖「不碰钱、不碰外部动作」；把自动放款塞进去，等于让一个纯 DAG 执行器持有资金能力， 与「AI 不碰钱、结论只由确定性分诊给出」的边界自相矛盾。
 *
 * <p><b>为什么不让 Controller 直接串两个服务</b>：那会把「执行完就消费」这条业务规则藏在入站适配器里——换个入口（批处理、 MQ）就漏掉。用例编排放应用层，入口只调用一次。
 *
 * <p><b>消费 = 两条动作，缺一不可</b>：
 *
 * <ol>
 *   <li><b>初审段收口</b>：决策已产出 ⇒ 案件必须离开「初审中」（11/13 → 12）。缺这一步的后果不是"少推一格"， 而是案件永久停在 13——既进不了核赔队列（{@code
 *       ReviewTaskStatusMapper.WAITING} 只认 40），也永远不会结案 （无终态 ⇒ 评测导出没有弱标签、{@code agreementRate} 恒
 *       {@code null}）。修复前实测：16 次决策执行、 0 个案件离开 13。
 *   <li><b>按结论路由</b>：{@code AUTO_APPROVE} → 结案 + 建单（受开关/上限/租户开关约束，未放行则转人工）； {@code
 *       NEED_MORE_EVIDENCE} → 补录中（21）；其余（{@code MANUAL_REQUIRED} / {@code SUGGEST_REJECT} / 执行失败）→
 *       待核赔（40）。
 * </ol>
 *
 * <p><b>金额口径</b>：核定金额只在自动通过路径上回填（{@link #completePreExam} 的金额参数），且必须 ≤ 申请金额；其余结论一律 {@code
 * null}——理算结果只是建议值，替人工预填等于"AI 替审核员定价"。自动通过不满足金额前提（无理算/越界）时不猜金额、 直接转人工并留受控原因码。
 *
 * <p><b>失败语义</b>：决策执行成功而结论消费被跳过（开关关闭 / 超上限 / 非自动通过）<b>不算失败</b>——决策记录已落库、
 * 轨迹可回放；跳过原因以受控码写日志，供「为什么这单没自动放款」反查。路由本身抛异常则照实向上抛（状态机拒绝跃迁必须让人看见， 不能静默当作"已消费"）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionRunApplicationService {

  private final DecisionGraphExecutor decisionGraphExecutor;
  private final AutoApprovalApplicationService autoApprovalApplicationService;
  private final DecisionExecutionApplicationService decisionExecutionApplicationService;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final IndemnityApplicationService indemnityApplicationService;

  /** 系统操作人标识（决策结论的消费是机器动作，审阅日志按此值筛出全部机器跃迁）。 */
  private static final String SYSTEM_OPERATOR = AutoApprovalApplicationService.SYSTEM_OPERATOR;

  /**
   * 执行结果 + 结论消费结果（消费被跳过时为受控原因码，不影响执行结果本身）。
   *
   * @param consumptionReason 自动通过结论的消费结果（{@code APPLIED} 或受控跳过原因）；非自动通过固定 {@code
   *     NOT_AUTO_APPROVE}——它描述的是<b>资金动作</b>，不是路由
   * @param claimStatus 本次路由后的案件落点（受控状态名，落库表示）；路由被跳过时为读取到的原状态
   * @param routingReason 路由结果受控码：{@code APPLIED} / {@code ESCALATED_AFTER_SKIP} / {@code
   *     ROUTED_TO_ADJUST} / {@code ROUTED_TO_EVIDENCE} / {@code NO_INDEMNITY} / {@code
   *     INDEMNITY_EXCEEDS_CLAIM} / {@code INVALID_INDEMNITY} / {@code ALREADY_ROUTED} / {@code
   *     CLAIM_NOT_IN_PRE_EXAM} / {@code AUTO_APPROVE_NOT_CONSUMABLE}
   */
  public record DecisionRunResult(
      DecisionExecutionResult execution,
      String consumptionReason,
      String claimStatus,
      String routingReason) {}

  /** 执行一次决策图，消费结论，并把案件推进到该结论对应的落点。 */
  public DecisionRunResult execute(long claimId, DecisionVersions versions) {
    DecisionExecutionResult execution = decisionGraphExecutor.execute(claimId, versions);

    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    ClaimStatus status = claim.status();
    boolean autoApprove = DecisionResult.AUTO_APPROVE.name().equals(execution.decisionResult());

    // ① 初审段收口（11/13 → 12）。重复执行/已被人推进的案件不重复跃迁（状态机本身也会拒绝）。
    AutoApproveAmount autoAmount = autoApprove ? autoApproveAmount(claimId, claim) : null;
    // 不可用的金额一律回填 null：越界值送进领域层会被「赔付 ≤ 索赔」不变量直接拒绝（案件又留在 13）。
    BigDecimal approvedAmount =
        autoAmount != null && autoAmount.usable() ? autoAmount.value() : null;
    if (status == ClaimStatus.PRE_EXAMINING || status == ClaimStatus.AUTO_PRE_EXAMINING) {
      claimCaseApplicationService.completePreExam(claimId, approvedAmount, SYSTEM_OPERATOR);
      status = ClaimStatus.PRE_EXAMINED;
    }

    // ② 按结论路由
    String consumptionReason = "NOT_AUTO_APPROVE";
    Routing routing;
    if (autoApprove) {
      if (status != ClaimStatus.PRE_EXAMINED) {
        // 已结案/已转人工/未开始初审：本次结论不具备消费前提，如实回传落点，不制造第二次跃迁
        routing = new Routing(status, "AUTO_APPROVE_NOT_CONSUMABLE");
      } else if (autoAmount != null && !autoAmount.usable()) {
        routing = escalate(claimId, autoAmount.rejectReason());
      } else {
        AutoApprovalOutcome outcome = autoApprovalApplicationService.applyAutoDecision(claimId);
        consumptionReason = outcome.reason();
        if (!outcome.applied()) {
          log.info("[决策执行] 自动通过结论未消费: claimId={}, reason={}", claimId, outcome.reason());
        }
        recordConsumption(execution, outcome.reason());
        // 未放行（开关关闭/超上限/租户禁用/已结案）⇒ 必须转人工，绝不把案件留在「已初审」等一个不会来的动作
        routing =
            outcome.applied()
                ? new Routing(ClaimStatus.FINISHED, "APPLIED")
                : escalate(claimId, "ESCALATED_AFTER_SKIP");
      }
    } else if (status != ClaimStatus.PRE_EXAMINED) {
      // 已在核赔段/终态（重复执行）或未进入初审段（10）：不做任何跃迁
      boolean notStarted = status == ClaimStatus.PENDING_PRE_EXAM;
      routing = new Routing(status, notStarted ? "CLAIM_NOT_IN_PRE_EXAM" : "ALREADY_ROUTED");
    } else if (DecisionResult.NEED_MORE_EVIDENCE.name().equals(execution.decisionResult())) {
      claimCaseApplicationService.requestEvidence(claimId, SYSTEM_OPERATOR);
      routing = new Routing(ClaimStatus.EVIDENCE_SUBMITTING, "ROUTED_TO_EVIDENCE");
    } else {
      routing = escalate(claimId, "ROUTED_TO_ADJUST");
    }

    log.info(
        "[决策执行] claimId={}, decision={}, status={}, claimStatus={}, routing={}, consumption={}",
        claimId,
        execution.decisionResult(),
        execution.status(),
        routing.status(),
        routing.reason(),
        consumptionReason);
    return new DecisionRunResult(
        execution, consumptionReason, routing.status().name(), routing.reason());
  }

  // ===================== 内部 =====================

  /** 路由结果（落点 + 受控原因码；两者都进响应与日志，读侧不必从状态反推原因）。 */
  private record Routing(ClaimStatus status, String reason) {}

  /** 自动通过可用金额（缺失/越界时 {@code rejectReason} 非空，路由据此转人工而不是猜金额）。 */
  private record AutoApproveAmount(BigDecimal value, String rejectReason) {

    boolean usable() {
      return rejectReason == null;
    }
  }

  private Routing escalate(long claimId, String reason) {
    claimCaseApplicationService.escalate(claimId, SYSTEM_OPERATOR);
    return new Routing(ClaimStatus.PENDING_ADJUST, reason);
  }

  /**
   * 自动通过所需金额（理算结果为资金权威口径）。
   *
   * <p>缺失 ⇒ {@code null}（不给金额，也不放行）；越界（> 申请金额，违反「赔付 ≤ 索赔」不变量）⇒ 同样不放行—— 这里选<b>转人工 +
   * 留痕</b>而不是抛异常：抛出去会把案件留在「初审中」，正是本类要修的形态；而金额不一致恰恰是最需要人 工核对的情形，交给人比留在机器断点更有价值。
   */
  private AutoApproveAmount autoApproveAmount(long claimId, ClaimCase claim) {
    AdjustmentResult latest = indemnityApplicationService.latest(claimId);
    if (latest == null || latest.totalAmount() == null) {
      return new AutoApproveAmount(null, "NO_INDEMNITY");
    }
    BigDecimal total = latest.totalAmount();
    if (total.signum() < 0) {
      log.warn("[决策执行] 理算金额为负，自动通过改走人工: claimId={}, total={}", claimId, total);
      return new AutoApproveAmount(null, "INVALID_INDEMNITY");
    }
    if (total.compareTo(claim.claimAmount()) > 0) {
      log.warn(
          "[决策执行] 理算金额超申请金额（不变量破坏），自动通过改走人工: claimId={}, total={}, claimAmount={}",
          claimId,
          total,
          claim.claimAmount());
      return new AutoApproveAmount(total, "INDEMNITY_EXCEEDS_CLAIM");
    }
    return new AutoApproveAmount(total, null);
  }

  /**
   * 把消费结果落到执行记录上（读侧只读轨迹时靠它回答「已自动通过，资金为什么没动」）。
   *
   * <p><b>为什么在这里落而不是在 {@link AutoApprovalApplicationService}</b>：那里只有 {@code claimId}，
   * 而执行结束后活跃位已释放，用「案件最新一次执行」反查可能命中并发起的另一次执行；这里手里有本次 执行的 {@code traceId}，是唯一精确的锚点。
   *
   * <p><b>为什么失败只告警</b>：资金动作（结案 + 建结算单）此时已经成功，把写诊断信息失败升级成接口 失败，会让客户端重试并可能重复建单。降级为 WARN
   * 并在日志里带全上下文，是这一处唯一可接受的取舍。
   */
  private void recordConsumption(DecisionExecutionResult execution, String reason) {
    try {
      decisionExecutionApplicationService.recordConsumption(execution.traceId(), reason);
    } catch (RuntimeException e) {
      log.warn(
          "[决策执行] 消费结果落库失败（执行本身已成功，读侧将看不到跳过原因）: claimId={}, traceId={}, reason={}",
          execution.claimId(),
          execution.traceId(),
          reason,
          e);
    }
  }
}
