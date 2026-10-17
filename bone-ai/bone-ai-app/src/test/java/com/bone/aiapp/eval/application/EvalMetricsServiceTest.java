package com.bone.aiapp.eval.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalMetricsServiceTest {

  private final EvalMetricsService service = new EvalMetricsService();

  @Test
  void perfect_run_yields_full_scores() {
    List<EvalRecord> recs =
        List.of(new EvalRecord(true, true, "c1"), new EvalRecord(false, false, null));
    EvalMetrics m = service.compute(recs);
    assertEquals(2, m.total());
    assertEquals(1, m.tp());
    assertEquals(1, m.tn());
    assertEquals(1.0, m.precision());
    assertEquals(1.0, m.recall());
    assertEquals(1.0, m.f1());
    assertEquals(0.0, m.missRate());
    assertEquals(0.0, m.falsePositiveRate());
    assertEquals(1.0, m.adoptionRate());
    assertEquals(1.0, m.citationAccuracy());
  }

  @Test
  void miss_and_false_positive_reflected() {
    // 应命中未命中(FN) + 不应命中却命中(FP)，且 FP 漏引用
    List<EvalRecord> recs =
        List.of(
            new EvalRecord(true, false, null), // FN，漏引用
            new EvalRecord(false, true, null)); // FP，漏引用
    EvalMetrics m = service.compute(recs);
    assertEquals(1, m.fn());
    assertEquals(1, m.fp());
    assertEquals(0.0, m.recall()); // 遗漏率=1
    assertEquals(1.0, m.missRate());
    assertEquals(1.0, m.falsePositiveRate());
    assertEquals(0.0, m.citationAccuracy()); // 需引用样本 1，引用 0
  }

  @Test
  void empty_input_is_safe() {
    EvalMetrics m = service.compute(List.of());
    assertEquals(0, m.total());
    assertEquals(0.0, m.precision());
    assertTrue(m.citationAccuracy() >= 0);
  }

  @Test
  void citation_accuracy_counts_only_required() {
    // 2 条需引用（均应或实命中），其中 1 条带引用
    List<EvalRecord> recs =
        List.of(
            new EvalRecord(true, true, "c1"),
            new EvalRecord(false, true, null), // 实命中但漏引用
            new EvalRecord(false, false, null)); // 不需引用
    EvalMetrics m = service.compute(recs);
    assertEquals(0.5, m.citationAccuracy());
  }
}
