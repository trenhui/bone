package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  public AnalyticsSummary summary(Instant from, Instant to) {
    requireWindow(from, to);
    List<DecisionExecution> rows = decisionExecutionRepository.findConvergedInWindow(from, to);
    if (rows == null) {
      rows = List.of();
    }

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
            .filter(java.util.Objects::nonNull)
            .mapToLong(e -> e.getDurationMs() == null ? 0L : e.getDurationMs().longValue())
            .average()
            .orElse(0.0);

    return new AnalyticsSummary(
        from,
        to,
        rows.size(),
        Map.copyOf(byResult),
        fallback,
        shadowRate,
        avgDurationMs,
        "影子自动化率 = AUTO_APPROVE / 已收敛执行数；自动放款开关默认关闭、E1/E2 未交付，此值不等于实际出金率");
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

  /** 聚合结果；{@code byDecisionResult} 用受控枚举键，不回传自由文本。 */
  public record AnalyticsSummary(
      Instant windowFrom,
      Instant windowTo,
      long converged,
      Map<String, Long> byDecisionResult,
      long fallbackCount,
      double shadowAutomationRate,
      double avgDurationMs,
      String scopeNote) {}
}
