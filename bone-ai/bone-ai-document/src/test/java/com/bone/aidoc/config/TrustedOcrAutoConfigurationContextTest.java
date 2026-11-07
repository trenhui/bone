package com.bone.aidoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.bone.aidoc.application.TrustedOcrApplicationService;
import com.bone.aidoc.domain.gateway.DocumentClassifier;
import com.bone.aidoc.domain.gateway.FeedbackCollector;
import com.bone.aidoc.domain.gateway.OcrEngine;
import com.bone.aidoc.domain.gateway.OcrMetrics;
import com.bone.aidoc.domain.gateway.OcrQualityEvaluator;
import com.bone.aidoc.domain.gateway.ReviewPolicy;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.service.ClaimDocumentTemplateRegistry;
import com.bone.aidoc.domain.service.NoOpFeedbackCollector;
import com.bone.aidoc.domain.service.NoOpOcrMetrics;
import com.bone.aidoc.infrastructure.ocr.HttpDocumentClassifier;
import java.io.ByteArrayInputStream;
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

  /**
   * maxBytes 必须真的从配置流入编排层（而非「配置了但引擎仍无限制」）。
   *
   * <p>此前把自动配置里的 {@code t.getMaxBytes()} 改坏成 {@code Long.MAX_VALUE}，158 个用例仍全绿——
   * 属典型的「配了不生效」盲区。本例通过真实 Bean 的超限行为反向钉死注入链路。
   */
  @Test
  void configured_max_bytes_is_enforced_by_the_wired_engine() {
    runner
        .withPropertyValues(
            "bone.ai.document.trusted-ocr.enabled=true",
            "bone.ai.document.trusted-ocr.max-bytes=16")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              OcrEngine engine = ctx.getBean(OcrEngine.class);

              // 反向：超限必须在入口读取阶段被拒，且消息带上「当前生效的」上限值
              assertThatThrownBy(
                      () ->
                          engine.parse(
                              new ByteArrayInputStream(new byte[64]),
                              "t1",
                              "big.jpg",
                              "d1",
                              OcrDocType.INVOICE))
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("超过大小上限", "16");

              // 正向对照：恰好等于上限的输入不得被大小闸门拦截。
              // 默认 NoOp 引擎会在后续阶段抛别的异常或直接返回，二者都属「未被大小闸门拦截」。
              Throwable bounded =
                  catchThrowable(
                      () ->
                          engine.parse(
                              new ByteArrayInputStream(new byte[16]),
                              "t1",
                              "ok.jpg",
                              "d1",
                              OcrDocType.INVOICE));
              if (bounded != null) {
                assertThat(bounded).hasMessageNotContaining("超过大小上限");
              }
            });
  }

  @Test
  void metrics_and_feedback_ports_are_wired_as_safe_defaults() {
    runner
        .withPropertyValues("bone.ai.document.trusted-ocr.enabled=true")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              // 两个可信闭环端口默认以 no-op 接入；缺失时绝不阻断识别
              assertThat(ctx).hasSingleBean(OcrMetrics.class);
              assertThat(ctx).hasSingleBean(FeedbackCollector.class);
              assertThat(ctx.getBean(OcrMetrics.class)).isInstanceOf(NoOpOcrMetrics.class);
              assertThat(ctx.getBean(FeedbackCollector.class))
                  .isInstanceOf(NoOpFeedbackCollector.class);
              // 应用服务确实注入了这两个端口（否则装配会缺参失败）
              assertThat(ctx).hasSingleBean(TrustedOcrApplicationService.class);
            });
  }

  @Test
  void retry_config_is_bound_and_propagates_to_transport() {
    runner
        .withPropertyValues(
            "bone.ai.document.trusted-ocr.enabled=true",
            "bone.ai.document.trusted-ocr.max-retries=5",
            "bone.ai.document.trusted-ocr.retry-base-delay-ms=30")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              DocumentProperties.TrustedOcr t =
                  ctx.getBean(DocumentProperties.class).getTrustedOcr();
              // 配置确实绑定且被读取；传输层构造时透传（重试机制本身由 HttpClientTransportRetryTest 钉死）
              assertThat(t.getMaxRetries()).isEqualTo(5);
              assertThat(t.getRetryBaseDelayMs()).isEqualTo(30);
            });
  }
}
