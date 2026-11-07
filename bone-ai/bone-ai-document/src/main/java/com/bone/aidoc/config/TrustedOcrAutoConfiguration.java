package com.bone.aidoc.config;

import com.bone.aidoc.application.TrustedOcrApplicationService;
import com.bone.aidoc.domain.gateway.DocumentClassifier;
import com.bone.aidoc.domain.gateway.FieldValidationRule;
import com.bone.aidoc.domain.gateway.OcrEngine;
import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.gateway.OcrQualityEvaluator;
import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.RiskThreshold;
import com.bone.aidoc.domain.service.AmountCaseConsistencyRule;
import com.bone.aidoc.domain.service.ClaimDocumentTemplateRegistry;
import com.bone.aidoc.domain.service.ClaimFieldRisk;
import com.bone.aidoc.domain.service.DateLegalityRule;
import com.bone.aidoc.domain.service.DefaultReviewPolicy;
import com.bone.aidoc.domain.service.DelegatingOcrProvider;
import com.bone.aidoc.domain.service.HeuristicOcrQualityEvaluator;
import com.bone.aidoc.domain.service.IdCardCheckDigitRule;
import com.bone.aidoc.domain.service.NoOpDocumentClassifier;
import com.bone.aidoc.domain.service.NoOpOcrClient;
import com.bone.aidoc.domain.service.NumericAmountRule;
import com.bone.aidoc.domain.service.TrustedOcrEngine;
import com.bone.aidoc.infrastructure.ocr.HttpDocumentClassifier;
import com.bone.aidoc.infrastructure.ocr.HttpOcrClient;
import com.bone.aidoc.infrastructure.ocr.JevHttpQualityEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 可信 OCR 管线自动配置（ADR-0046 骨架装配，默认关闭）。
 *
 * <p>{@code bone.ai.document.trusted-ocr.enabled=true} 才生效，对既有上下文零影响。 所有 Bean 均
 * {@code @ConditionalOnMissingBean}：真实 Jev 质量评估器、真实票种分类器、自定义版式契约或引擎都 以同类型 Bean 覆盖即可，无需改动本配置。
 *
 * <p>引擎接入：配置 {@code paddle-url} / {@code glm-url} / {@code minicpm-url} 即装配对应真实 HTTP
 * 适配器；各适配器声明通用票种能力，<b>按票种的引擎优先顺序由 {@link ClaimDocumentTemplateRegistry}
 * 单点定义</b>（避免同一语义两处配置）。GLM-OCR 同时作为 L3 多模型交叉的次引擎。
 *
 * <p>阈值唯一口径为 {@code risk-thresholds.<LEVEL>.*}；旧扁平阈值不采信且显式配置会拒绝启动。 配置 {@code jev-url} / {@code
 * classifier-url} 后，真实 HTTP 适配器覆盖启发式质量评估与 NoOp 分类器， 二者均 fail-closed：服务异常时质量分不可用 / 票种回落 GENERAL。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "bone.ai.document.trusted-ocr.enabled", havingValue = "true")
@EnableConfigurationProperties(DocumentProperties.class)
public class TrustedOcrAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OcrQualityEvaluator trustedOcrQualityEvaluator(DocumentProperties props) {
    DocumentProperties.TrustedOcr t = props.getTrustedOcr();
    if (!isBlank(t.getJevUrl())) {
      // 配置 Jev 服务即接入真实标定质量分；服务异常时该适配器 fail-closed（质量分 NaN →
      // CRITICAL 强制二次识别），绝不用启发式分数伪装成模型判断。
      return new JevHttpQualityEvaluator(
          t.getJevUrl(), new HttpOcrClient.HttpClientTransport(t.getTimeoutMs()));
    }
    // 默认装配模型无关的启发式质量评估器（ADR-0046 Phase-3），让 L3 质量感知路由生效；
    // 该实现是代理信号而非 Jev 类标定模型，真实质量评估器以同类型 Bean 覆盖本实现即可。
    // 若需显式关闭质量分（强制 CRITICAL 转二次识别），自行注册 NoOpOcrQualityEvaluator。
    return new HeuristicOcrQualityEvaluator();
  }

  @Bean
  @ConditionalOnMissingBean
  public DocumentClassifier trustedDocumentClassifier(DocumentProperties props) {
    DocumentProperties.TrustedOcr t = props.getTrustedOcr();
    if (!isBlank(t.getClassifierUrl())) {
      // 配置分类服务即接入真实票种识别；服务异常时该适配器 fail-closed（回落 GENERAL），
      // 绝不猜测票种——错分会连带错选引擎、错用规则与模板。
      return new HttpDocumentClassifier(
          t.getClassifierUrl(),
          new HttpOcrClient.HttpClientTransport(t.getTimeoutMs()),
          t.getMaxBytes());
    }
    // ② 票种分类端口的默认安全态：不猜测票种，保持调用方显式指定的类型。
    // 真实分类器（CNN/VLM 票种识别）以同类型 Bean 覆盖后，仅在调用方传 GENERAL 时参与判定。
    return new NoOpDocumentClassifier();
  }

  @Bean
  @ConditionalOnMissingBean
  public ClaimDocumentTemplateRegistry trustedDocumentTemplates() {
    // 版式契约（必填字段 + 引擎偏好）唯一事实源；生产可用经 PoC 校准的自定义 Bean 整体替换。
    return ClaimDocumentTemplateRegistry.builtIn();
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewPolicy trustedReviewPolicy(DocumentProperties props) {
    DocumentProperties.TrustedOcr t = props.getTrustedOcr();
    if (t.hasLegacyFlatThresholds()) {
      // 「配了不生效」比「没这个配置」更危险：显式配置旧扁平阈值时拒绝启动，而非静默忽略。
      throw new IllegalStateException(
          "bone.ai.document.trusted-ocr.*-threshold 扁平阈值已不采信；唯一口径为 "
              + "risk-thresholds.<LEVEL>.*，请移除扁平配置后按风险等级分别标定");
    }
    Map<RiskLevel, RiskThreshold> map = DefaultReviewPolicy.defaultThresholds();
    t.getRiskThresholds()
        .forEach(
            (level, cfg) -> {
              RiskThreshold threshold =
                  new RiskThreshold(
                      cfg.getAutoPassConfidence(),
                      cfg.getAutoPassQuality(),
                      cfg.getManualConfidence(),
                      cfg.getManualQuality());
              validateThresholds(level, threshold);
              map.put(level, threshold);
            });
    return new DefaultReviewPolicy(map);
  }

  @Bean
  @ConditionalOnMissingBean
  public OcrEngine trustedOcrEngine(
      DocumentProperties props,
      OcrQualityEvaluator qualityEvaluator,
      ReviewPolicy reviewPolicy,
      DocumentClassifier classifier,
      ClaimDocumentTemplateRegistry templates) {
    DocumentProperties.TrustedOcr t = props.getTrustedOcr();
    List<OcrProvider> providers = new ArrayList<>();
    if (!isBlank(t.getPaddleUrl())) {
      providers.add(buildProvider("paddle-ocr", t.getPaddleUrl(), t));
    }
    if (!isBlank(t.getGlmUrl())) {
      providers.add(buildProvider("glm-ocr", t.getGlmUrl(), t));
    }
    if (!isBlank(t.getMinicpmUrl())) {
      providers.add(buildProvider("minicpm-ocr", t.getMinicpmUrl(), t));
    }
    if (providers.isEmpty()) {
      // 未配置任何引擎：安全态，包住 NoOpOcrClient，不阻断装配。
      providers.add(new DelegatingOcrProvider(new NoOpOcrClient(), "noop", Set.of()));
    }
    // GLM-OCR 作为 L3 多模型交叉的次引擎（若已接入）
    OcrProvider cross =
        providers.stream().filter(p -> "glm-ocr".equals(p.engineId())).findFirst().orElse(null);

    List<FieldValidationRule> rules =
        List.of(
            new NumericAmountRule(),
            new DateLegalityRule(),
            new IdCardCheckDigitRule(),
            new AmountCaseConsistencyRule());

    return new TrustedOcrEngine(
        providers,
        qualityEvaluator,
        reviewPolicy,
        new ClaimFieldRisk(templates),
        rules,
        cross,
        templates,
        classifier,
        t.getMaxBytes());
  }

  @Bean
  @ConditionalOnMissingBean
  public TrustedOcrApplicationService trustedOcrApplicationService(OcrEngine engine) {
    return new TrustedOcrApplicationService(engine);
  }

  /** 阈值合法性：落在 [0,1] 且人工复核门槛不得高于自动通过门槛（否则策略语义反转）。 */
  private static void validateThresholds(RiskLevel level, RiskThreshold t) {
    for (double v :
        List.of(
            t.autoPassConfidence(), t.autoPassQuality(), t.manualConfidence(), t.manualQuality())) {
      if (Double.isNaN(v) || v < 0.0 || v > 1.0) {
        throw new IllegalStateException("risk-thresholds." + level + " 阈值须在 [0,1] 且非 NaN: " + t);
      }
    }
    if (t.manualConfidence() > t.autoPassConfidence() || t.manualQuality() > t.autoPassQuality()) {
      throw new IllegalStateException("risk-thresholds." + level + " 人工复核阈值不得高于自动通过阈值: " + t);
    }
  }

  private OcrProvider buildProvider(String engineId, String url, DocumentProperties.TrustedOcr t) {
    var client =
        new HttpOcrClient(
            url,
            engineId,
            t.getMaxBytes(),
            new HttpOcrClient.HttpClientTransport(t.getTimeoutMs()));
    return new DelegatingOcrProvider(client, engineId, Set.of());
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
