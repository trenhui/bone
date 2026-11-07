package com.bone.aidoc.application;

import com.bone.aidoc.domain.gateway.FeedbackCollector;
import com.bone.aidoc.domain.gateway.OcrEngine;
import com.bone.aidoc.domain.gateway.OcrMetrics;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.ReviewFeedback;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import com.bone.aidoc.domain.service.ClaimDocumentTemplateRegistry;
import com.bone.aidoc.domain.service.ClaimFieldRisk;
import com.bone.aidoc.domain.service.NoOpFeedbackCollector;
import com.bone.aidoc.domain.service.NoOpOcrMetrics;
import com.bone.aidoc.domain.service.OcrAcceptanceMetrics;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可信 OCR 应用服务（ADR-0046 §七：OCR 与理赔业务分离的薄门面）。
 *
 * <p>只做委托、度量上报与反馈采集，不持有领域规则（领域规则在 {@link OcrEngine} 及其编排内）。 与既有 {@code
 * DocumentParseApplicationService} 同范式：解析/识别的编排属于域，应用层不加业务判断。
 *
 * <p>Phase-7 增强（NIST AI RMF Measure & Monitor + Human-in-the-loop）：
 *
 * <ul>
 *   <li><b>生产可观测性</b>：每次解析后向 {@link OcrMetrics} 上报决策分布、关键字段自动通过、 L4 一致性失配、fail-open
 *       安全闸门拦截次数与票种分类结果， 使生产侧能检测<b>漂移</b>（如安全闸门骤降常预示模型静默退化）。
 *   <li><b>反馈闭环</b>：{@link #recordFeedback} 采集人工最终裁定，既是生产准确率度量，也是下一轮阈值标定的真值来源。
 * </ul>
 *
 * <p>度量与反馈端口均为可选（默认 no-op）；缺失时只削弱观测/验证能力，绝不改变复核决策或阻断识别。
 */
public class TrustedOcrApplicationService {

  private static final Logger log = LoggerFactory.getLogger(TrustedOcrApplicationService.class);

  private final OcrEngine engine;
  private final OcrMetrics metrics;
  private final FeedbackCollector feedback;
  private final ClaimFieldRisk fieldRisk;

  /** 便捷构造：未装配度量/反馈后端与版式契约时使用安全默认（no-op + 内置模板）。 */
  public TrustedOcrApplicationService(OcrEngine engine) {
    this(
        engine,
        new NoOpOcrMetrics(),
        new NoOpFeedbackCollector(),
        new ClaimFieldRisk(ClaimDocumentTemplateRegistry.builtIn()));
  }

  /**
   * 全参构造（自动配置装配入口）。
   *
   * @param metrics 生产可观测性端口（真实实现以同类型 Bean 覆盖）
   * @param feedback 人工复核反馈采集端口
   * @param fieldRisk 字段风险分级（决定「关键字段」口径，供关键字段自动通过度量）
   */
  public TrustedOcrApplicationService(
      OcrEngine engine, OcrMetrics metrics, FeedbackCollector feedback, ClaimFieldRisk fieldRisk) {
    this.engine = engine;
    this.metrics = metrics;
    this.feedback = feedback;
    this.fieldRisk = fieldRisk;
  }

  /**
   * 对一份理赔材料执行可信 OCR 解析，并上报本次解析的度量。
   *
   * @param input 文档流
   * @param tenantId 租户
   * @param sourceRef 来源引用
   * @param docId 文档标识；为空自动生成
   * @param docType 文档类型（驱动识别 schema 与规则）
   */
  public TrustedOcrDocument parse(
      InputStream input, String tenantId, String sourceRef, String docId, OcrDocType docType) {
    TrustedOcrDocument doc = engine.parse(input, tenantId, sourceRef, docId, docType);
    reportMetrics(doc);
    log.info(
        "Trusted OCR parsed: doc={}, tenant={}, docType={}, engine={}, autoPassRate={}, needsHuman={}, l4Mismatch={}, safetyGates={}",
        doc.docId(),
        tenantId,
        docType,
        doc.primaryEngineId(),
        String.format("%.2f", doc.autoPassRate()),
        doc.needsHumanAttention(),
        l4MismatchOf(doc),
        safetyGatesOf(doc));
    return doc;
  }

  /**
   * 采集一条人工最终裁定（字段级）。
   *
   * <p>这是「可信闭环」的关键入口：引擎决策后，人工在理赔系统中确认或纠正时调用本方法， 把 {@link
   * ReviewFeedback#isConfirmedError()}（自动放行却被判错）这一最危险的失察纳入度量与标定真值。
   */
  public void recordFeedback(ReviewFeedback feedback) {
    if (feedback == null) {
      return;
    }
    this.feedback.record(feedback);
    if (feedback.isConfirmedError()) {
      log.warn(
          "可信 OCR 确认失察：doc={}, field={}, engineValue={}, humanValue={}（自动放行但人工判定错误）",
          feedback.docId(),
          feedback.fieldName(),
          feedback.engineValue(),
          feedback.humanValue());
    }
  }

  private void reportMetrics(TrustedOcrDocument doc) {
    for (Map.Entry<com.bone.aidoc.domain.model.ReviewStatus, Integer> e :
        doc.decisionCounts().entrySet()) {
      for (int i = 0; i < e.getValue(); i++) {
        metrics.countDecision(e.getKey());
      }
    }
    OcrAcceptanceMetrics m = OcrAcceptanceMetrics.from(doc, fieldRisk);
    for (int i = 0; i < m.criticalAutoPassed(); i++) {
      metrics.countCriticalAutoPass();
    }
    metrics.countL4Mismatch(m.l4MismatchCount());
    metrics.countSafetyGate(m.safetyGateCount());
    metrics.countClassification(doc.docType());
  }

  private int l4MismatchOf(TrustedOcrDocument doc) {
    int n = 0;
    for (com.bone.aidoc.domain.model.OcrFieldEvidence f : doc.fields()) {
      if (doc.decisionReasons().getOrDefault(f.fieldName(), "").contains("[L4]")) {
        n++;
      }
    }
    return n;
  }

  private int safetyGatesOf(TrustedOcrDocument doc) {
    int n = 0;
    for (com.bone.aidoc.domain.model.OcrFieldEvidence f : doc.fields()) {
      if (f.reviewStatus() != com.bone.aidoc.domain.model.ReviewStatus.AUTO_PASSED
          && isSafetyGate(doc.decisionReasons().getOrDefault(f.fieldName(), ""))) {
        n++;
      }
    }
    return n;
  }

  private static boolean isSafetyGate(String reason) {
    return reason.contains("[L1]")
        || reason.contains("[L2]")
        || reason.contains("[L3']")
        || reason.contains("[L3]")
        || reason.contains("[值]")
        || reason.contains("[来源]")
        || reason.contains("[配置]");
  }
}
