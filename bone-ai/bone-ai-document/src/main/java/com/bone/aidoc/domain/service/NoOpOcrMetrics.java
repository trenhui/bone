package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.OcrMetrics;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.ReviewStatus;

/**
 * {@link OcrMetrics} 的安全默认实现：丢弃全部计数。
 *
 * <p>当骨架未装配真实度量后端时启用，确保「度量缺失」永远不会阻断识别或改变复核决策 （度量只是观测，不是决策输入）。真实度量后端以同类型 Bean 覆盖即可。
 */
public class NoOpOcrMetrics implements OcrMetrics {

  @Override
  public void countDecision(ReviewStatus status) {}

  @Override
  public void countCriticalAutoPass() {}

  @Override
  public void countL4Mismatch(int n) {}

  @Override
  public void countSafetyGate(int n) {}

  @Override
  public void countClassification(OcrDocType docType) {}

  @Override
  public Snapshot snapshot() {
    return Snapshot.zero();
  }
}
