package com.bone.claim.application;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 理算运维编排（§10.2 内部端点背后的批量入口：存量重算 / 规则回归）。
 *
 * <p><b>为什么批量循环不放进 {@link IndemnityApplicationService}</b>：同类内自调用不走 Spring 代理，
 * {@code @Transactional} 逐案事务会静默失效——批量的正确粒度恰恰是"一案一事务"： 第 N 案失败不能回滚第 1..N-1 案已落库的重算，也不能让整批共用一个大事务
 * （存量回溯动的是钱，跨案长事务既放大锁窗口又违反一事务一聚合）。 故本服务<b>不标事务</b>，逐案经代理调用受事务保护的成员方法，逐案捕获领域异常计入报告。
 */
@Service
@RequiredArgsConstructor
public class AdjustmentOpsApplicationService {

  /** 单批案件数上限：内部运维入口也要设边界，防止误传全量 id 打爆事务/连接池。 */
  public static final int MAX_BATCH = 100;

  private final IndemnityApplicationService indemnityApplicationService;

  /** 单案重算条目。 */
  public record RecomputeItem(
      long claimId,
      String outcome,
      BigDecimal oldTotalAmount,
      BigDecimal newTotalAmount,
      boolean totalChanged,
      String message) {}

  /** 存量重算请求。 */
  public record RecomputeReq(List<Long> claimIds, String ruleVersion, boolean dryRun) {}

  /**
   * 存量回溯重算（§10.2）：{@code dryRun=true} 走试算预览（只算不落库，出差异报告）， {@code dryRun=false} 真重算（逐案事务；已 CONFIRMED
   * 的案件由聚合拒绝，记为 REJECTED 而不中断整批）。
   *
   * <p>Go/No-Go（§12.2）要求「存量重算 dryRun 差异报告验证，且确认未回写已支付/已结案案件」—— 已确认案件在 dryRun=false
   * 时只会在报告里出现，绝不会被改写。
   */
  public List<RecomputeItem> recompute(RecomputeReq req) {
    List<Long> claimIds = req.claimIds() == null ? List.of() : req.claimIds();
    if (claimIds.isEmpty()) {
      throw new DomainException("存量重算须指定案件（claimIds 不能为空）");
    }
    if (claimIds.size() > MAX_BATCH) {
      throw new DomainException("单批重算不得超过 " + MAX_BATCH + " 案，请分批执行");
    }
    List<RecomputeItem> report = new ArrayList<>(claimIds.size());
    for (Long claimId : claimIds) {
      if (claimId == null) {
        continue;
      }
      report.add(recomputeOne(claimId, req.ruleVersion(), req.dryRun()));
    }
    return report;
  }

  private RecomputeItem recomputeOne(long claimId, String ruleVersion, boolean dryRun) {
    IndemnityApplicationService.AdjustmentPreview preview;
    try {
      preview = indemnityApplicationService.preview(claimId, ruleVersion);
    } catch (DomainException ex) {
      return new RecomputeItem(claimId, "FAILED", null, null, false, ex.getMessage());
    }
    if (dryRun) {
      return new RecomputeItem(
          claimId,
          "PREVIEWED",
          preview.baselineTotalAmount(),
          preview.candidateTotalAmount(),
          preview.totalChanged(),
          null);
    }
    try {
      IndemnityApplicationService.AdjustmentResult result =
          indemnityApplicationService.recalculate(claimId, ruleVersion);
      return new RecomputeItem(
          claimId,
          "RECALCULATED",
          preview.baselineTotalAmount(),
          result.totalAmount(),
          preview.baselineTotalAmount() == null
              || result.totalAmount().compareTo(preview.baselineTotalAmount()) != 0,
          null);
    } catch (DomainException ex) {
      // 典型：已 CONFIRMED（§5.3 硬不变量：已付额度禁止重复释放）。逐案报告，不中断整批。
      return new RecomputeItem(
          claimId, "REJECTED", preview.baselineTotalAmount(), null, false, ex.getMessage());
    }
  }

  /** 回归基准条目：案件 + 期望理算总额（可选期望快照哈希）。 */
  public record BaselineCase(
      long claimId, BigDecimal expectedTotalAmount, String expectedFormulaHash) {}

  /** 回归请求：基准集 + 目标规则版本（缺省 = 当前生效规则）。 */
  public record RegressionReq(String ruleVersion, List<BaselineCase> baseline) {}

  /** 回归结果：逐案判定 + 汇总（bone-ai-rules 发布门禁的判据）。 */
  public record RegressionReport(
      int totalCases, int passed, int failed, List<RegressionFailure> failures) {}

  public record RegressionFailure(
      long claimId,
      BigDecimal expectedTotalAmount,
      BigDecimal actualTotalAmount,
      String expectedFormulaHash,
      String actualFormulaHash) {}

  /**
   * 规则基准集回归（§10.2）：对基准集逐案试算（只算不落库），实际输出与期望不符即失败。
   *
   * <p><b>一期边界（ADR-002 豁免如实登记）</b>：bone-ai-rules 六端口未接线，决策分诊为域内确定性
   * 实现；本回归覆盖的是<b>理算引擎</b>（费用池→比例→免赔→控额→归因）对基准集的输出一致性—— 规则/责任版本发布前跑一遍，任何一案金额或快照哈希漂移即拦截发布。
   */
  public RegressionReport regression(RegressionReq req) {
    List<BaselineCase> baseline = req.baseline() == null ? List.of() : req.baseline();
    if (baseline.isEmpty()) {
      throw new DomainException("回归基准集不能为空（baseline 至少一条）");
    }
    if (baseline.size() > MAX_BATCH) {
      throw new DomainException("单批回归不得超过 " + MAX_BATCH + " 案，请分批执行");
    }
    List<RegressionFailure> failures = new ArrayList<>();
    for (BaselineCase c : baseline) {
      IndemnityApplicationService.AdjustmentPreview preview;
      try {
        preview = indemnityApplicationService.preview(c.claimId(), req.ruleVersion());
      } catch (DomainException ex) {
        failures.add(
            new RegressionFailure(
                c.claimId(), c.expectedTotalAmount(), null, c.expectedFormulaHash(), null));
        continue;
      }
      boolean totalMismatch =
          preview.candidateTotalAmount() == null
              || c.expectedTotalAmount() == null
              || preview.candidateTotalAmount().compareTo(c.expectedTotalAmount()) != 0;
      boolean hashMismatch =
          c.expectedFormulaHash() != null
              && !c.expectedFormulaHash().equals(preview.candidateFormulaHash());
      if (totalMismatch || hashMismatch) {
        failures.add(
            new RegressionFailure(
                c.claimId(),
                c.expectedTotalAmount(),
                preview.candidateTotalAmount(),
                c.expectedFormulaHash(),
                preview.candidateFormulaHash()));
      }
    }
    return new RegressionReport(
        baseline.size(), baseline.size() - failures.size(), failures.size(), failures);
  }
}
