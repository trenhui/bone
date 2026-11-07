package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.ReviewStatus;
import java.util.EnumMap;
import java.util.Map;

/** 测试用度量实现：记录各类计数，供断言验证「应用层确实上报」。 */
public class RecordingOcrMetrics implements OcrMetrics {

  private final Map<ReviewStatus, Integer> decisions = new EnumMap<>(ReviewStatus.class);
  private int criticalAutoPass;
  private int l4Mismatch;
  private int safetyGates;
  private int classifications;

  @Override
  public void countDecision(ReviewStatus status) {
    decisions.merge(status, 1, Integer::sum);
  }

  @Override
  public void countCriticalAutoPass() {
    criticalAutoPass++;
  }

  @Override
  public void countL4Mismatch(int n) {
    l4Mismatch += n;
  }

  @Override
  public void countSafetyGate(int n) {
    safetyGates += n;
  }

  @Override
  public void countClassification(OcrDocType docType) {
    classifications++;
  }

  @Override
  public Snapshot snapshot() {
    return new Snapshot(
        decisions.getOrDefault(ReviewStatus.AUTO_PASSED, 0),
        decisions.getOrDefault(ReviewStatus.MANUAL_REVIEW, 0),
        decisions.getOrDefault(ReviewStatus.SECONDARY_RECOGNITION, 0),
        criticalAutoPass,
        l4Mismatch,
        safetyGates);
  }

  public int decisionsOf(ReviewStatus status) {
    return decisions.getOrDefault(status, 0);
  }

  public int classifications() {
    return classifications;
  }
}
