package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.ReviewStatus;

/**
 * 可信 OCR 生产可观测性端口（NIST AI RMF Measure & Monitor）。
 *
 * <p>骨架只定义计数契约与 no-op 默认实现；真实实现（Micrometer / Prometheus 计数器、日志聚合） 以同类型 Bean
 * 覆盖即可。引擎/应用层向本端口上报，使生产侧能检测<b>漂移</b>： 自动通过率突增、人工干预率攀升、L4 一致性失配频发、fail-open 安全闸门骤降
 * （后一种骤降往往预示模型静默退化——错误不再被拦截，而非质量变好）。
 *
 * <p>本端口不持有任何业务规则，仅聚合计数；是否为线程安全由实现方保证 （no-op 与常见度量库实现均为线程安全）。
 */
public interface OcrMetrics {

  /** 记录一次字段级复核决策。 */
  void countDecision(ReviewStatus status);

  /** 记录一次关键字段被自动通过（CRITICAL 且 AUTO_PASSED）。 */
  void countCriticalAutoPass();

  /** 记录 L4 业务一致性失配次数（明细汇总 ≠ 总额）。 */
  void countL4Mismatch(int n);

  /** 记录一次被 fail-open 安全闸门拦截（阻止了本会被错误自动放行的字段）。 */
  void countSafetyGate(int n);

  /** 记录一次票种分类结果。 */
  void countClassification(OcrDocType docType);

  /** 当前计数快照（no-op 返回全 0 的不可变快照）。 */
  Snapshot snapshot();

  /** 计数快照（不可变）。 */
  record Snapshot(
      long autoPassed,
      long manualReview,
      long secondaryRecognition,
      long criticalAutoPassed,
      long l4Mismatch,
      long safetyGates) {
    public static Snapshot zero() {
      return new Snapshot(0, 0, 0, 0, 0, 0);
    }
  }
}
