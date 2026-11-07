package com.bone.aidoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.application.TrustedOcrApplicationService;
import com.bone.aidoc.domain.gateway.DocumentClassifier;
import com.bone.aidoc.domain.gateway.OcrEngine;
import com.bone.aidoc.domain.gateway.OcrQualityEvaluator;
import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.service.ClaimDocumentTemplateRegistry;
import com.bone.aidoc.infrastructure.ocr.HttpDocumentClassifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 真实 Spring 上下文装配守护（直接 new 配置类测不到 Bean 注入与条件装配）。 */
class TrustedOcrAutoConfigurationContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TrustedOcrAutoConfiguration.class));

  @Test
  void disabled_by_default_leaves_existing_context_untouched() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).doesNotHaveBean(OcrEngine.class);
          assertThat(ctx).doesNotHaveBean(DocumentClassifier.class);
          assertThat(ctx).doesNotHaveBean(ClaimDocumentTemplateRegistry.class);
        });
  }

  @Test
  void enabled_wires_all_ports_with_safe_defaults() {
    runner
        .withPropertyValues("bone.ai.document.trusted-ocr.enabled=true")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).hasSingleBean(OcrEngine.class);
              assertThat(ctx).hasSingleBean(OcrQualityEvaluator.class);
              assertThat(ctx).hasSingleBean(ReviewPolicy.class);
              assertThat(ctx).hasSingleBean(DocumentClassifier.class);
              assertThat(ctx).hasSingleBean(ClaimDocumentTemplateRegistry.class);
              assertThat(ctx).hasSingleBean(TrustedOcrApplicationService.class);
              // 真实装配（含新增的两个端口注入）成功，且引擎落到 NoOp 安全态
              assertThat(ctx.getBean(OcrEngine.class)).isNotNull();
            });
  }

  @Test
  void custom_classifier_bean_overrides_noop_default() {
    runner
        .withPropertyValues("bone.ai.document.trusted-ocr.enabled=true")
        .withBean(DocumentClassifier.class, () -> (input, sourceRef) -> OcrDocType.INVOICE)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).hasSingleBean(DocumentClassifier.class);
              assertThat(ctx.getBean(DocumentClassifier.class).classify(null, "s"))
                  .isEqualTo(OcrDocType.INVOICE);
              assertThat(ctx).hasSingleBean(OcrEngine.class);
            });
  }

  @Test
  void configured_jev_and_classifier_urls_override_safe_defaults() {
    runner
        .withPropertyValues(
            "bone.ai.document.trusted-ocr.enabled=true",
            "bone.ai.document.trusted-ocr.jev-url=http://127.0.0.1:8500",
            "bone.ai.document.trusted-ocr.classifier-url=http://127.0.0.1:8600")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              // 真实 HTTP 适配器覆盖启发式/NoOp 默认；构造期不发起网络调用
              assertThat(ctx.getBean(OcrQualityEvaluator.class).evaluatorId())
                  .isEqualTo("jev-http");
              assertThat(ctx.getBean(DocumentClassifier.class))
                  .isInstanceOf(HttpDocumentClassifier.class);
              assertThat(ctx).hasSingleBean(OcrEngine.class);
            });
  }

  @Test
  void legacy_flat_threshold_prevents_context_startup() {
    runner
        .withPropertyValues(
            "bone.ai.document.trusted-ocr.enabled=true",
            "bone.ai.document.trusted-ocr.auto-pass-threshold=0.50")
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class);
            });
  }
}
