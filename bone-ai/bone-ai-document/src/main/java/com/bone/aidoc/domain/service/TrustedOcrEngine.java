package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.DocumentClassifier;
import com.bone.aidoc.domain.gateway.FieldValidationRule;
import com.bone.aidoc.domain.gateway.OcrEngine;
import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.gateway.OcrQualityEvaluator;
import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.QualityReport;
import com.bone.aidoc.domain.model.RequiredField;
import com.bone.aidoc.domain.model.ReviewOutcome;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 可信 OCR 编排引擎（ADR-0046 七层管线 + 五层可信度的纯域实现）。
 *
 * <p>不调用任何模型 SDK，只编排领域端口，保证 OCR 作为独立领域边界（§七）。 流程：① 文档接入 → ② 票种分类与按票种引擎路由 → ③ 主引擎识别 → ④ 字段级证据组装 → ⑤
 * L2 规则校验 → ⑤' L4 业务一致性 → ⑥ L3 质量评估与多模型交叉 → ⑦ 复核路由与必填字段缺失拦截。
 *
 * <p>设计要点：可信度是<b>字段级</b>而非文档级；多模型一致 ≠ 正确（分歧必升级）；CRITICAL 字段缺质量确认时强制二次识别； 票种声明的必填字段若引擎未返回，补一条 FAILED
 * 证据并强制人工复核——漏识别不得静默消失。
 *
 * <p><b>入口边界（Phase-6）</b>：空流 / 超限 / 主引擎空结果一律编码化抛出交上层转 HITL， 绝不产出「字段为空、需人工数为 0」的空证据链假绿；{@code
 * maxBytes} 在入口处做真有界读取， 不依赖下游适配器（否则内存已全量分配，防护形同虚设）。
 */
public class TrustedOcrEngine implements OcrEngine {

  /** 兼容既有构造：不注入大小上限时保持无限制（生产由配置显式注入）。 */
  private static final long UNBOUNDED = Long.MAX_VALUE;

  private static final int BUFFER_SIZE = 8192;

  private final List<OcrProvider> providers;
  private final OcrQualityEvaluator qualityEvaluator;
  private final ReviewPolicy reviewPolicy;
  private final ClaimFieldRisk fieldRisk;
  private final List<FieldValidationRule> rules;
  private final OcrProvider crossProvider;
  private final ClaimDocumentTemplateRegistry templates;
  private final DocumentClassifier classifier;
  private final long maxBytes;
  private final L4BusinessConsistencyValidator l4Validator;

  public TrustedOcrEngine(
      List<OcrProvider> providers,
      OcrQualityEvaluator qualityEvaluator,
      ReviewPolicy reviewPolicy,
      ClaimFieldRisk fieldRisk) {
    this(
        providers,
        qualityEvaluator,
        reviewPolicy,
        fieldRisk,
        List.of(),
        null,
        ClaimDocumentTemplateRegistry.builtIn(),
        null);
  }

  public TrustedOcrEngine(
      List<OcrProvider> providers,
      OcrQualityEvaluator qualityEvaluator,
      ReviewPolicy reviewPolicy,
      ClaimFieldRisk fieldRisk,
      List<FieldValidationRule> rules) {
    this(
        providers,
        qualityEvaluator,
        reviewPolicy,
        fieldRisk,
        rules,
        null,
        ClaimDocumentTemplateRegistry.builtIn(),
        null);
  }

  public TrustedOcrEngine(
      List<OcrProvider> providers,
      OcrQualityEvaluator qualityEvaluator,
      ReviewPolicy reviewPolicy,
      ClaimFieldRisk fieldRisk,
      List<FieldValidationRule> rules,
      OcrProvider crossProvider) {
    this(
        providers,
        qualityEvaluator,
        reviewPolicy,
        fieldRisk,
        rules,
        crossProvider,
        ClaimDocumentTemplateRegistry.builtIn(),
        null);
  }

  /** 显式指定模板注册表（单测可用 {@code noTemplates()} 隔离模板行为）。 */
  public TrustedOcrEngine(
      List<OcrProvider> providers,
      OcrQualityEvaluator qualityEvaluator,
      ReviewPolicy reviewPolicy,
      ClaimFieldRisk fieldRisk,
      List<FieldValidationRule> rules,
      OcrProvider crossProvider,
      ClaimDocumentTemplateRegistry templates) {
    this(
        providers,
        qualityEvaluator,
        reviewPolicy,
        fieldRisk,
        rules,
        crossProvider,
        templates,
        null);
  }

  /** 全参构造：额外注入文档分类器（仅当调用方传入 GENERAL 时启用）。 */
  public TrustedOcrEngine(
      List<OcrProvider> providers,
      OcrQualityEvaluator qualityEvaluator,
      ReviewPolicy reviewPolicy,
      ClaimFieldRisk fieldRisk,
      List<FieldValidationRule> rules,
      OcrProvider crossProvider,
      ClaimDocumentTemplateRegistry templates,
      DocumentClassifier classifier) {
    this(
        providers,
        qualityEvaluator,
        reviewPolicy,
        fieldRisk,
        rules,
        crossProvider,
        templates,
        classifier,
        UNBOUNDED);
  }

  /** 生产装配入口：额外注入单份文件大小上限（字节，来自 {@code trusted-ocr.max-bytes}）。 */
  public TrustedOcrEngine(
      List<OcrProvider> providers,
      OcrQualityEvaluator qualityEvaluator,
      ReviewPolicy reviewPolicy,
      ClaimFieldRisk fieldRisk,
      List<FieldValidationRule> rules,
      OcrProvider crossProvider,
      ClaimDocumentTemplateRegistry templates,
      DocumentClassifier classifier,
      long maxBytes) {
    this.providers = providers == null ? List.of() : providers;
    this.qualityEvaluator = qualityEvaluator;
    this.reviewPolicy = reviewPolicy;
    this.fieldRisk = fieldRisk;
    this.rules = rules == null ? List.of() : rules;
    this.crossProvider = crossProvider;
    this.templates = templates == null ? ClaimDocumentTemplateRegistry.noTemplates() : templates;
    this.classifier = classifier;
    this.maxBytes = maxBytes > 0 ? maxBytes : UNBOUNDED;
    this.l4Validator = new L4BusinessConsistencyValidator(this.templates);
  }

  @Override
  public TrustedOcrDocument parse(
      InputStream input, String tenantId, String sourceRef, String docId, OcrDocType declaredType) {
    // ① 输入读入内存一次，供分类、主/次引擎复用（InputStream 不可重读）
    byte[] bytes = readAll(input, sourceRef);

    // ② 票种分类（仅调用方未指定时用分类器重判）+ 按票种路由引擎
    OcrDocType docType = resolveDocType(declaredType, bytes, sourceRef);
    List<OcrProvider> candidates = routeProviders(docType, sourceRef);
    OcrProvider primary = candidates.get(0);

    // ③ 主引擎识别；空结果不得产出「看似正常」的空证据链
    OcrResult result = primary.recognize(new ByteArrayInputStream(bytes), sourceRef, docType);
    if (result == null || result.fields() == null || result.fields().isEmpty()) {
      throw new IllegalStateException(
          "主引擎未返回任何字段: "
              + sourceRef
              + "（engine="
              + primary.engineId()
              + "）；空证据链会被误判为「无需人工」，不得产出可信文档，请转人工复核");
    }
    String id = (docId == null || docId.isBlank()) ? UUID.randomUUID().toString() : docId;

    // ④ 字段级证据组装（按引擎返回页码逐字段定位；无页码回退 1；坐标透传）
    List<OcrFieldEvidence> evidence =
        result.fields().stream()
            .map(f -> OcrFieldEvidence.from(f, primary.engineId()))
            .collect(Collectors.toCollection(ArrayList::new));

    // ⑤ L2 规则校验（逐字段聚合，FAILED 立即定级；保留规则 ID 与失败原因供审计）
    List<OcrFieldEvidence> validated = new ArrayList<>();
    Map<String, String> reasons = new LinkedHashMap<>();
    for (OcrFieldEvidence e : evidence) {
      ValidationOutcome vo = validateField(e, docType);
      validated.add(e.withValidationStatus(vo.status()));
      if (!vo.reasons().isEmpty()) {
        reasons.put(e.fieldName(), String.join(" | ", vo.reasons()));
      }
    }

    // ⑤' L4 业务一致性（费用明细汇总 vs 总额）：不一致时总额字段降级 FAILED
    L4BusinessConsistencyValidator.Outcome l4 = l4Validator.validate(result, validated, docType);
    validated = l4.fields();
    l4.reasons().forEach((k, v) -> reasons.merge(k, v, (a, b) -> a + " | " + b));

    // ⑥ L3 质量评估（不重写原值）
    QualityReport qualityReport = qualityEvaluator.evaluate(validated, docType, sourceRef);

    // ⑥' L3 多模型交叉校验（可选次引擎）
    Map<String, CrossCheck> cross = Collections.emptyMap();
    if (crossProvider != null && crossProvider.supports(docType)) {
      OcrResult secondary =
          crossProvider.recognize(new ByteArrayInputStream(bytes), sourceRef, docType);
      cross = L3CrossValidator.compare(result, secondary);
    }

    // ⑦ 复核路由（字段级、按风险分级，融合交叉信号；逐字段记录判据）
    Map<ReviewStatus, Integer> decisionCounts = new EnumMap<>(ReviewStatus.class);
    List<OcrFieldEvidence> routed = new ArrayList<>();
    for (OcrFieldEvidence e : validated) {
      RiskLevel risk = fieldRisk.levelOf(docType, e.fieldName());
      CrossCheck crossStatus = cross.getOrDefault(e.fieldName(), CrossCheck.NOT_RUN);
      ReviewOutcome outcome =
          reviewPolicy.decideWithReason(
              new FieldReviewContext(e, qualityReport.scoreFor(e.fieldName()), risk, crossStatus));
      ReviewStatus status = toReviewStatus(outcome.decision());
      routed.add(e.withReviewStatus(status));
      decisionCounts.merge(status, 1, Integer::sum);
      reasons.merge(e.fieldName(), outcome.reason(), (a, b) -> a + " | " + b);
    }

    // ⑦' 必填字段缺失拦截：在质量评估之后追加，避免零置信占位证据污染质量分的中位数基准
    List<String> recognizedNames =
        validated.stream().map(OcrFieldEvidence::fieldName).collect(Collectors.toList());
    for (RequiredField required : templates.missingRequiredFields(docType, recognizedNames)) {
      routed.add(OcrFieldEvidence.missing(required.canonicalName(), primary.engineId(), docType));
      decisionCounts.merge(ReviewStatus.MANUAL_REVIEW, 1, Integer::sum);
      reasons.merge(
          required.canonicalName(), "[必填] 模板声明字段未被识别，补 FAILED 证据并强制人工复核", (a, b) -> a + " | " + b);
    }

    return new TrustedOcrDocument(
        id,
        tenantId,
        sourceRef,
        docType,
        primary.engineId(),
        candidates.stream().map(OcrProvider::engineId).collect(Collectors.toList()),
        routed,
        qualityReport,
        result.rawText(),
        result.pageCount(),
        decisionCounts,
        Collections.unmodifiableMap(new LinkedHashMap<>(reasons)));
  }

  /** ② 分类：调用方显式指定的票种优先；仅 GENERAL 且已注入分类器时才重判。 */
  private OcrDocType resolveDocType(OcrDocType declared, byte[] bytes, String sourceRef) {
    OcrDocType type = declared == null ? OcrDocType.GENERAL : declared;
    if (type != OcrDocType.GENERAL || classifier == null) {
      return type;
    }
    OcrDocType classified = classifier.classify(new ByteArrayInputStream(bytes), sourceRef);
    return classified == null ? type : classified;
  }

  /** ② 路由：先按 supports 过滤，再按票种模板的引擎偏好稳定排序（未列入偏好者保持装配顺序）。 */
  private List<OcrProvider> routeProviders(OcrDocType docType, String sourceRef) {
    List<OcrProvider> candidates =
        providers.stream()
            .filter(p -> p.supports(docType))
            .collect(Collectors.toCollection(ArrayList::new));
    if (candidates.isEmpty()) {
      // 配置错误：没有任何引擎支持该票种。编码化抛出，交由上层转 HITL，不伪造空结果。
      throw new IllegalStateException(
          "no OcrProvider supports docType=" + docType + " for " + sourceRef);
    }
    List<String> preferred = templates.preferredEngineOrder(docType);
    if (!preferred.isEmpty()) {
      candidates.sort(
          Comparator.comparingInt(
              p -> {
                int idx = preferred.indexOf(p.engineId());
                return idx < 0 ? preferred.size() : idx;
              }));
    }
    return candidates;
  }

  /** 入口真有界读取：超限立即停止，不先全量分配内存；空流显式拒绝。 */
  private byte[] readAll(InputStream input, String sourceRef) {
    if (input == null) {
      throw new IllegalArgumentException("识别输入流为空: " + sourceRef);
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[BUFFER_SIZE];
      long total = 0;
      int n;
      while ((n = input.read(buffer)) != -1) {
        total += n;
        if (total > maxBytes) {
          throw new IllegalStateException(
              "识别文件超过大小上限 " + maxBytes + " 字节: " + sourceRef + "；已停止读取以防内存耗尽，请压缩或分册后重试");
        }
        out.write(buffer, 0, n);
      }
      byte[] bytes = out.toByteArray();
      if (bytes.length == 0) {
        throw new IllegalArgumentException("识别输入为空字节流: " + sourceRef + "；空输入不得产出空证据链假绿，请转人工复核");
      }
      return bytes;
    } catch (IOException e) {
      throw new IllegalStateException("读取识别文件失败: " + sourceRef, e);
    }
  }

  private record ValidationOutcome(ValidationStatus status, List<String> reasons) {}

  private ValidationOutcome validateField(OcrFieldEvidence field, OcrDocType docType) {
    if (rules.isEmpty()) {
      // 无规则时保持 PENDING（交由规则层后续补注册，不臆断通过）
      return new ValidationOutcome(ValidationStatus.PENDING, List.of());
    }
    ValidationStatus aggregated = ValidationStatus.PASSED;
    List<String> reasons = new ArrayList<>();
    for (FieldValidationRule rule : rules) {
      if (!rule.appliesTo().isEmpty() && !rule.appliesTo().contains(docType)) {
        continue;
      }
      if (!rule.appliesToField(field)) {
        continue;
      }
      var vr = rule.validate(field, docType);
      if (vr.status().isFailed()) {
        reasons.add("[" + rule.ruleId() + "] " + vr.message());
        return new ValidationOutcome(ValidationStatus.FAILED, reasons);
      }
      if (vr.status().isNotApplicable()) {
        aggregated = ValidationStatus.NOT_APPLICABLE;
      }
    }
    return new ValidationOutcome(aggregated, reasons);
  }

  private static ReviewStatus toReviewStatus(com.bone.aidoc.domain.model.ReviewDecision decision) {
    return switch (decision) {
      case AUTO_PASS -> ReviewStatus.AUTO_PASSED;
      case SECONDARY_RECOGNIZE -> ReviewStatus.SECONDARY_RECOGNITION;
      case MANUAL_REVIEW -> ReviewStatus.MANUAL_REVIEW;
    };
  }
}
