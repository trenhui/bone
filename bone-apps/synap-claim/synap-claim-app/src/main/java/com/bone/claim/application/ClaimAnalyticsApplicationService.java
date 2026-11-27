package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.projection.CaseDurationProjection;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 只读运营分析出口（G0「度量先行」的最小承载）。
 *
 * <p>此前系统能产出 AI 结论，却没有通道回答「AI 到底介入了多少、降级了多少、有多大比例本可自动通过」，
 * 自动化率只能靠文档承诺。本服务把决策执行记录聚合成可复算的指标，且明确标注<b>影子口径</b>： {@code shadowAutomationRate} 是决策图产出 {@code
 * AUTO_APPROVE} 的占比， <b>不等于实际出金率</b>——自动放款开关默认关闭、E1/E2 硬闸未交付前本就不会出金。 把影子值写成「已自动结案率」是典型的指标造假，故返回体自带
 * {@code scopeNote}。
 *
 * <p>与评测样本导出的分工：那边导「单案弱标签」供模型评测，这里出「聚合指标」供运营大盘，两者不重复。 只读、不写任何业务表，不含报案号与被保人标识。
 */
@Service
@RequiredArgsConstructor
public class ClaimAnalyticsApplicationService {

  /** 时间窗上限：与评测导出一致，避免一次查询扫全库。 */
  public static final int MAX_WINDOW_DAYS = 31;

  private final DecisionExecutionRepository decisionExecutionRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final DomainEventOutboxRepository domainEventOutboxRepository;
  private final TenantPort tenantPort;

  /** 推送成功/失败状态（与 {@code DomainEventOutbox#STATUS_SENT} 同源，死信计入失败）。 */
  private static final String OUTBOX_SENT = "SENT";

  private static final String OUTBOX_FAILED = "FAILED";
  private static final String OUTBOX_DEAD = "DEAD";

  public AnalyticsSummary summary(Instant from, Instant to) {
    requireWindow(from, to);
    List<DecisionExecution> rows = decisionExecutionRepository.findConvergedInWindow(from, to);
    if (rows == null) {
      rows = List.of();
    }

    DecisionAggregate agg = aggregateDecisions(rows);

    // ---- 复核 / 单案时长 / 推送：三条独立统计口径，同一时间窗 ----
    Timestamp windowFrom = Timestamp.from(from);
    Timestamp windowTo = Timestamp.from(to);
    List<CaseDurationProjection> durationRows =
        claimCaseRepository.caseDurationsInWindow(windowFrom, windowTo);
    if (durationRows == null) {
      durationRows = List.of();
    }
    // 领域投影 → 毫秒样本（仓储不返回裸 List<Long>，见 CaseDurationProjection）。
    List<Long> durations =
        durationRows.stream()
            .map(CaseDurationProjection::getDurationMs)
            .filter(Objects::nonNull)
            .toList();
    // 分母取窗口内已结案案件数（时长样本即全体结案案件）；分子取第二复核人已介入的案件数。
    long reviewedCount = durations.size();
    long secondReviewedCount = claimCaseRepository.countReviewedInWindow(windowFrom, windowTo);
    long pushSucceeded =
        domainEventOutboxRepository.countByStatusInWindow(windowFrom, windowTo, OUTBOX_SENT);
    long pushFailed =
        domainEventOutboxRepository.countByStatusInWindow(windowFrom, windowTo, OUTBOX_FAILED)
            + domainEventOutboxRepository.countByStatusInWindow(windowFrom, windowTo, OUTBOX_DEAD);

    return new AnalyticsSummary(
        from,
        to,
        rows.size(),
        Map.copyOf(agg.byResult()),
        agg.fallback(),
        agg.shadowRate(),
        agg.avgDurationMs(),
        SCOPE_NOTE,
        reviewedCount,
        secondReviewedCount,
        percentile(durations, 0.5),
        percentile(durations, 0.9),
        pushSucceeded,
        pushFailed);
  }

  /** 影子口径固定说明：任何分组结果都必须原样带回，禁止改写成「实际出金率」。 */
  private static final String SCOPE_NOTE =
      "影子自动化率 = AUTO_APPROVE / 已收敛执行数；自动放款开关默认关闭、E1/E2 未交付，此值不等于实际出金率";

  /**
   * 按当前租户分组的只读运营指标（租户隔离在服务端强制）。
   *
   * <p>租户身份来自 {@link TenantPort#currentTenantId()}（认证主体唯一权威，ArchUnit 禁止本层直读
   * TenantContext），不接收前端传参；因此调用方无法越权查他人租户。只聚合可从 {@link DecisionExecution} 拆分的可复算指标；case
   * 时长分位/复核率/推送无 tenant 维度，不在此端点返回（不造可拆分的假象）。无维度场景请继续用 {@link #summary(Instant, Instant)}。
   */
  public TenantAnalyticsSummary summaryByTenant(Instant from, Instant to) {
    requireWindow(from, to);
    long tenantId = tenantPort.currentTenantId();
    List<DecisionExecution> all = decisionExecutionRepository.findConvergedInWindow(from, to);
    List<DecisionExecution> rows =
        all == null
            ? List.of()
            : all.stream()
                .filter(e -> e != null && Long.valueOf(tenantId).equals(e.getTenantId()))
                .toList();
    DecisionAggregate agg = aggregateDecisions(rows);
    return new TenantAnalyticsSummary(
        tenantId,
        from,
        to,
        rows.size(),
        Map.copyOf(agg.byResult()),
        agg.fallback(),
        agg.shadowRate(),
        agg.avgDurationMs(),
        SCOPE_NOTE);
  }

  /** 决策执行记录的共用聚合内核：原全量 summary 与租户分组 summary 复用同一口径。 */
  private static DecisionAggregate aggregateDecisions(List<DecisionExecution> rows) {
    Map<String, Long> byResult = new LinkedHashMap<>();
    for (DecisionExecution execution : rows) {
      if (execution == null) {
        continue;
      }
      String key =
          execution.getDecisionResult() == null || execution.getDecisionResult().isBlank()
              ? "UNKNOWN"
              : execution.getDecisionResult();
      byResult.merge(key, 1L, Long::sum);
    }
    long fallback =
        rows.stream().filter(e -> e != null && "FALLBACK".equals(e.getStatus())).count();
    long auto = byResult.getOrDefault("AUTO_APPROVE", 0L);
    double shadowRate = rows.isEmpty() ? 0.0 : (double) auto / rows.size();
    double avgDurationMs =
        rows.stream()
            .filter(Objects::nonNull)
            .mapToLong(e -> e.getDurationMs() == null ? 0L : e.getDurationMs().longValue())
            .average()
            .orElse(0.0);
    return new DecisionAggregate(byResult, fallback, shadowRate, avgDurationMs);
  }

  private record DecisionAggregate(
      Map<String, Long> byResult, long fallback, double shadowRate, double avgDurationMs) {}

  /**
   * 排序后取第 {@code ceil(p*n)} 个（1-based）样本：<b>不是平均值冒充分位数</b>。
   *
   * <p>单案时长是重尾分布，少量卡单（如 10^9 ms）会把均值抬高几个数量级，但 P50/P90 不动；用均值冒充 P90 会让运营以为「九成案件都很慢」，是典型的指标造假。空样本返回
   * {@code null}，前端显示「—」而不是 0。
   */
  private static Double percentile(List<Long> values, double p) {
    List<Long> sorted =
        values.stream()
            .filter(Objects::nonNull)
            .sorted()
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    if (sorted.isEmpty()) {
      return null;
    }
    int rank = (int) Math.ceil(p * sorted.size());
    int index = Math.max(0, Math.min(rank - 1, sorted.size() - 1));
    return sorted.get(index).doubleValue();
  }

  private static void requireWindow(Instant from, Instant to) {
    if (from == null || to == null || !from.isBefore(to)) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID, "时间窗必填且 from 必须早于 to");
    }
    if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_WINDOW_DAYS)) > 0) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID, "时间窗过大：最多 " + MAX_WINDOW_DAYS + " 天");
    }
  }

  /**
   * 聚合结果；{@code byDecisionResult} 用受控枚举键，不回传自由文本。
   *
   * <p>{@code reviewedCount} 为复核率分母（窗口内已结案案件数），{@code secondReviewedCount} 为分子（第二复核人已介入案件数）； 分位数为
   * {@code Double} 且空样本为 {@code null}，避免拿 0 冒充「零耗时」。Jackson 会把 long 序列化成字符串，前端按 {@code string |
   * number} 处理；Double 仍是数字。
   */
  public record AnalyticsSummary(
      Instant windowFrom,
      Instant windowTo,
      long converged,
      Map<String, Long> byDecisionResult,
      long fallbackCount,
      double shadowAutomationRate,
      double avgDurationMs,
      String scopeNote,
      long reviewedCount,
      long secondReviewedCount,
      Double caseDurationP50Ms,
      Double caseDurationP90Ms,
      long pushSucceeded,
      long pushFailed) {}

  /**
   * 当前租户分组指标（只读、服务端强制隔离）。
   *
   * <p>只含可从 {@link DecisionExecution} 按 tenantId 拆分的可复算指标；case 时长分位/复核率/推送因仓储无 tenant
   * 维度不在本结果内，避免伪造可拆分假象。{@code scopeNote} 必随结果返回，禁止把影子自动化率表述为实际出金率。
   */
  public record TenantAnalyticsSummary(
      long tenantId,
      Instant windowFrom,
      Instant windowTo,
      long converged,
      Map<String, Long> byDecisionResult,
      long fallbackCount,
      double shadowAutomationRate,
      double avgDurationMs,
      String scopeNote) {}
}
