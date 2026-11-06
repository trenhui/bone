package com.bone.aidoc.config;

import com.bone.aidoc.application.TrustedOcrApplicationService;
import com.bone.aidoc.domain.gateway.FieldValidationRule;
import com.bone.aidoc.domain.gateway.OcrEngine;
import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.gateway.OcrQualityEvaluator;
import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.RiskThreshold;
import com.bone.aidoc.domain.service.AmountCaseConsistencyRule;
import com.bone.aidoc.domain.service.ClaimFieldRisk;
import com.bone.aidoc.domain.service.DateLegalityRule;
import com.bone.aidoc.domain.service.DefaultReviewPolicy;
import com.bone.aidoc.domain.service.DelegatingOcrProvider;
import com.bone.aidoc.domain.service.IdCardCheckDigitRule;
import com.bone.aidoc.domain.service.NoOpOcrClient;
import com.bone.aidoc.domain.service.NoOpOcrQualityEvaluator;
import com.bone.aidoc.domain.service.NumericAmountRule;
import com.bone.aidoc.domain.service.TrustedOcrEngine;
import com.bone.aidoc.infrastructure.ocr.HttpOcrClient;
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
 * {@code @ConditionalOnMissingBean}： 真实 Jev 质量评估器、专用票种规则或自定义引擎都以同类型 Bean 覆盖即可，无需改动本配置。
 *
 * <p>引擎接入：配置 {@code paddle-url} / {@code glm-url} / {@code minicpm-url} 即装配对应真实 HTTP 适配器 （复用 {@link
 * HttpOcrClient}），不配置则不含该引擎；GLM-OCR 同时作为 L3 多模型交叉的次引擎。 未配置任何引擎时退化为 {@link NoOpOcrClient}
 * 安全态，保证装配不失败。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "bone.ai.document.trusted-ocr.enabled", havingValue = "true")
@EnableConfigurationProperties(DocumentProperties.class)
public class TrustedOcrAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OcrQualityEvaluator trustedOcrQualityEvaluator() {
    // TODO(ADR-0046 Phase-2)：接入 MiniCPM-OCR Jev 真实质量评估器，替换 NoOp（质量分不可用 → CRITICAL 字段自动转二次识别）。
    return new NoOpOcrQualityEvaluator();
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewPolicy trustedReviewPolicy(DocumentProperties props) {
    Map<RiskLevel, RiskThreshold> map = DefaultReviewPolicy.defaultThresholds();
    props
        .getTrustedOcr()
        .getRiskThresholds()
        .forEach(
            (level, cfg) ->
                map.put(
                    level,
                    new RiskThreshold(
                        cfg.getAutoPassConfidence(),
                        cfg.getAutoPassQuality(),
                        cfg.getManualConfidence(),
                        cfg.getManualQuality())));
    return new DefaultReviewPolicy(map);
  }

  @Bean
  @ConditionalOnMissingBean
  public OcrEngine trustedOcrEngine(
      DocumentProperties props, OcrQualityEvaluator qualityEvaluator, ReviewPolicy reviewPolicy) {
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
        providers, qualityEvaluator, reviewPolicy, new ClaimFieldRisk(), rules, cross);
  }

  @Bean
  @ConditionalOnMissingBean
  public TrustedOcrApplicationService trustedOcrApplicationService(OcrEngine engine) {
    return new TrustedOcrApplicationService(engine);
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
