package com.bone.aiapp.eval.application;

import com.bone.aiapp.eval.domain.model.EvalRun;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 评测指标计算（纯函数，无 DB 耦合，便于流水线复用与单测）。
 *
 * <p>以每条运行的「应命中 / 实命中」二值判定 TP/FP/TN/FN，推导 P/R/F1、遗漏率、误报率； 引用正确率 = 需引用样本（应或实命中）中带非空 {@code
 * citedClause} 的占比；采纳率以整体正确率（TP+TN）/total 作代理指标（阈值详设 §2.7 可校准）。
 */
@Component
public class EvalMetricsApplicationService {

  public EvalMetrics compute(List<EvalRecord> records) {
    long tp = 0, fp = 0, tn = 0, fn = 0;
    long requiresCitation = 0, cited = 0;
    for (EvalRecord r : records) {
      if (r.expectedHit() && r.predictedHit()) {
        tp++;
      } else if (!r.expectedHit() && r.predictedHit()) {
        fp++;
      } else if (r.expectedHit() && !r.predictedHit()) {
        fn++;
      } else {
        tn++;
      }
      if (r.expectedHit() || r.predictedHit()) {
        requiresCitation++;
        if (r.citedClause() != null && !r.citedClause().isBlank()) {
          cited++;
        }
      }
    }
    long total = records.size();
    double precision = safeDiv(tp, tp + fp);
    double recall = safeDiv(tp, tp + fn);
    double f1 = (precision + recall) == 0 ? 0 : 2 * precision * recall / (precision + recall);
    double missRate = safeDiv(fn, tp + fn);
    double falsePositiveRate = safeDiv(fp, fp + tn);
    double adoptionRate = total == 0 ? 0 : (double) (tp + tn) / total;
    double citationAccuracy = requiresCitation == 0 ? 1.0 : (double) cited / requiresCitation;
    return new EvalMetrics(
        total,
        tp,
        fp,
        tn,
        fn,
        precision,
        recall,
        f1,
        missRate,
        falsePositiveRate,
        adoptionRate,
        citationAccuracy);
  }

  public EvalMetrics computeFromRuns(List<EvalRun> runs) {
    return compute(
        runs.stream()
            .map(r -> new EvalRecord(r.isExpectedHit(), r.isPredictedHit(), r.getCitedClause()))
            .toList());
  }

  private static double safeDiv(long a, long b) {
    return b == 0 ? 0 : (double) a / b;
  }
}
