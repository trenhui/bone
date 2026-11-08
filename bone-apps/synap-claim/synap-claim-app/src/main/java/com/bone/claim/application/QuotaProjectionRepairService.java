package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaProjection;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.model.reconciliation.ReconciliationAction;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.ReconciliationActionRepository;
import com.bone.core.annotation.NoDomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 额度账户投影的<b>按流水重建</b>（运维修复入口，§11.7 不变量 1 ①的处置动作）。
 *
 * <p><b>为什么需要一个人工入口</b>：写侧自愈（{@code QuotaApplicationService#reconcileProjectionIfDeficit}）只在
 * 「冻结额不足以支撑本次释放」时才触发——它救的是被卡住的结算单，不是被对账判出来的漂移。对账每天都会 产出 {@code PROJECTION_DRIFT}
 * 差异单，但此前<b>没有任何动作能把它消掉</b>：差异单只能看，账户行只能等下一笔释放顺带修正， 「检出—处置—复核」这条链在处置处断掉。缺处置入口的巡检，时间久了就变成没人看的日报。
 *
 * <p><b>为什么这个入口<b>不需要</b>双人复核</b>（与此前设想相反，这里改判）：
 *
 * <ul>
 *   <li>它<b>不接受目标值</b>——请求体里没有"改成多少"这个字段。可执行的只有一件确定的事：{@code 投影 := f(流水)}。
 *       一旦允许指定目标值，性质就从"重算"变成"改账"，那时才必须两个人。
 *   <li>它<b>只写派生列</b>（{@code quota_frozen/quota_claimed/deductible_used/cap_used}），不写任何金额流水、不动
 *       {@code quota_initial}，因此不产生、也不消灭任何一笔钱。要真的改账，只能走补偿流水（那条路径本身已有双人复核）。
 *   <li>可逆且幂等：再跑一次结果相同；对账差异单是 append-only 的，重算不会把「曾经漂移过」这件事从证据里抹掉。
 * </ul>
 *
 * <p>真正的控制点因此是三条<b>硬拒绝</b>（错得安静比错得响更危险，这里是反过来：宁可拒绝也不做一次"看起来成功"的重算）：
 *
 * <ol>
 *   <li><b>流水为空而投影非零 ⇒ 拒绝</b>。空流水重算 = 把占用量归零 = 凭空放出额度，这是"用重算抹账"的形态。
 *   <li><b>重算后超出初始额度 ⇒ 拒绝</b>（不开自动放款的前提下，先让人核账，别让一次维护动作顺手把守恒破坏写进库）。
 *   <li><b>没有漂移 ⇒ 什么都不做</b>（{@code changed=false}），不写库、不打"已修复"的假日志。
 * </ol>
 *
 * <p><b>留痕</b>：动作落 {@code t_reconciliation_action}（{@code target_type=QUOTA_ACCOUNT}、{@code
 * action_type=PROJECTION_REBUILD}、{@code detail} 记逐列 before->after），同时保留一条 {@code WARN} 日志——
 * 日志是排障时的第一现场，流水是审计与回溯的台账，两者不互相替代。 {@code changed=false}（本来就没漂移）不落流水：没发生的事不该有动作记录。
 */
@Slf4j
@NoDomainEvent
@Service
@RequiredArgsConstructor
public class QuotaProjectionRepairService {

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  private final QuotaAccountRepository quotaAccountRepository;
  private final ReconciliationActionRepository reconciliationActionRepository;
  private final TenantPort tenantPort;

  /** 单列修复前后（差异单里"哪一列错了、差多少"的直接来源）。 */
  public record ColumnDelta(String field, BigDecimal before, BigDecimal after) {}

  /**
   * 修复结果。
   *
   * @param changed 是否真的改写了账户行（{@code false} = 投影本来就等于流水，幂等返回）
   * @param deltas 被改写的列；{@code changed=false} 时为空
   */
  public record RepairResult(long accountId, boolean changed, List<ColumnDelta> deltas) {}

  /**
   * 按流水重建某账户的投影（运维动作，须在租户上下文内调用）。
   *
   * @param accountId 额度账户 ID
   * @param operatorId 操作人（由接口层从鉴权身份取，不接受请求体自报）
   */
  @Transactional
  public RepairResult rebuildProjection(long accountId, String operatorId) {
    QuotaAccount account = quotaAccountRepository.findById(accountId);
    if (account == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND, accountId);
    }
    List<QuotaTransaction> ledger = quotaAccountRepository.findTransactions(accountId);
    QuotaProjection projection = QuotaProjection.rebuild(ledger);
    List<QuotaProjection.Drift> drifts = projection.driftAgainst(account);
    if (drifts.isEmpty()) {
      return new RepairResult(accountId, false, List.of());
    }
    if (ledger == null || ledger.isEmpty()) {
      // 没有流水却有占用：可能是流水被删（真丢数据），也可能是账户行被写脏。两种都不能靠"按空流水重算"解决——
      // 重算的语义是"以事实为准"，而此时事实本身缺失。清零等于把已用的额度还回去（超赔），故拒绝。
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_QUOTA_STATE_CONFLICT,
          "账户流水为空但投影非零，拒绝按空流水重算（会清零占用）: accountId=" + accountId);
    }
    if (projection.claimed().add(projection.frozen()).compareTo(nz(account.getQuotaInitial()))
        > 0) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_QUOTA_STATE_CONFLICT, "重算后占用超过初始额度，须人工核账: accountId=" + accountId);
    }

    List<ColumnDelta> deltas = new ArrayList<>(drifts.size());
    for (QuotaProjection.Drift drift : drifts) {
      deltas.add(new ColumnDelta(drift.field(), drift.projection(), drift.ledger()));
    }
    account.reconcileProjection(
        projection.frozen(), projection.claimed(), projection.deductibleUsed());
    quotaAccountRepository.save(account);
    long tenantId = tenantPort.currentTenantId();
    reconciliationActionRepository.save(
        ReconciliationAction.projectionRebuilt(
            tenantId,
            accountId,
            deltas.stream()
                .map(d -> new ReconciliationAction.QuotaDelta(d.field(), d.before(), d.after()))
                .toList(),
            operatorId,
            Instant.now()));
    log.warn(
        "[额度投影重建] operator={}, tenantId={}, accountId={}, deltas={}",
        operatorId,
        tenantId,
        accountId,
        deltas);
    return new RepairResult(accountId, true, List.copyOf(deltas));
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}
