package com.bone.claim.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/** 图像解析适配器：门控三态（默认关不装配）+ 解析成功/契约违反/mock 拒绝/网关不可用。 */
class ImageDocumentParsePortAdapterTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withUserConfiguration(ImageDocumentParsePortAdapter.class)
          .withBean(
              LlmGatewayApplicationService.class, () -> mock(LlmGatewayApplicationService.class));

  @Test
  @DisplayName("门控默认关：Bean 不装配（现网行为不变）")
  void gateOffByDefault() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean("imageDocumentParsePortAdapter"));
  }

  @Test
  @DisplayName("门控开：Bean 装配")
  void gateOnAssembles() {
    runner
        .withPropertyValues("bone.claim.document-parse.image.enabled=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(ImageDocumentParsePortAdapter.class));
  }

  private static ImageDocumentParsePortAdapter adapterReturning(String content) {
    LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
    when(gateway.invoke(any())).thenReturn(new LlmResponse(content, "m", "stop", LlmUsage.empty()));
    return new ImageDocumentParsePortAdapter(gateway);
  }

  @Test
  @DisplayName("成功路径：available=true + 白名单字段 + 坐标 image@code")
  void success() {
    var adapter =
        adapterReturning(
            "{\"fields\":[{\"code\":\"diagnosis_code\",\"value\":\"J06.9\",\"confidence\":0.88}]}");

    var doc = adapter.parseImage(DocType.MEDICAL_RECORD, "oss://bucket/x.jpg");

    assertThat(doc.available()).isTrue();
    assertThat(doc.reason()).isEqualTo(ImageDocumentParsePortAdapter.REASON_OK);
    assertThat(doc.fields()).hasSize(1);
    assertThat(doc.fields().get(0).evidenceRef()).isEqualTo("image@diagnosis_code");
    assertThat(doc.extractedJson()).contains("\"mode\":\"image\"");
  }

  @Test
  @DisplayName("mock 响应拒绝：IMAGE_MOCK_REJECTED（fail-closed，不冒充解析成功）")
  void mockRejected() {
    var adapter = adapterReturning("{\"mock\":true}");

    var doc = adapter.parseImage(DocType.INVOICE, "oss://bucket/x.jpg");

    assertThat(doc.available()).isFalse();
    assertThat(doc.reason()).isEqualTo(ImageDocumentParsePortAdapter.REASON_MOCK_REJECTED);
  }

  @Test
  @DisplayName(
      "网关异常 → IMAGE_GATEWAY_UNAVAILABLE；非 JSON/字段越界 → IMAGE_OUTPUT_INVALID；无 imageRef → IMAGE_REF_MISSING")
  void failureAttribution() {
    LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
    when(gateway.invoke(any())).thenThrow(new IllegalStateException("boom"));
    ImageDocumentParsePortAdapter failing = new ImageDocumentParsePortAdapter(gateway);

    assertThat(failing.parseImage(DocType.INVOICE, "oss://b/x.jpg").reason())
        .isEqualTo(ImageDocumentParsePortAdapter.REASON_GATEWAY_UNAVAILABLE);
    assertThat(adapterReturning("not-json").parseImage(DocType.INVOICE, "oss://b/x.jpg").reason())
        .isEqualTo(ImageDocumentParsePortAdapter.REASON_OUTPUT_INVALID);
    assertThat(
            adapterReturning("{\"fields\":[]}")
                .parseImage(DocType.INVOICE, "oss://b/x.jpg")
                .reason())
        .isEqualTo(ImageDocumentParsePortAdapter.REASON_OUTPUT_INVALID);
    assertThat(
            adapterReturning("{\"fields\":[]}")
                .parseImage(DocType.INVOICE, "oss://b/x.jpg")
                .fields())
        .isEmpty();
    assertThat(failing.parseImage(DocType.INVOICE, "  ").reason()).isEqualTo("IMAGE_REF_MISSING");
  }

  @Test
  @DisplayName("文本层 parse 委托确定性基线（图像通道不改变文本路径行为）")
  void textParseDelegatesToBaseline() {
    var adapter = adapterReturning("{}");

    var doc = adapter.parse(DocType.INVOICE, "发票号码:12345,合计金额:￥100.00");

    // 委托文本层基线：与 TextLayerDocumentParsePortAdapter 对同一输入的行为逐字段一致
    var expected =
        new TextLayerDocumentParsePortAdapter().parse(DocType.INVOICE, "发票号码:12345,合计金额:￥100.00");
    assertThat(doc.available()).isEqualTo(expected.available());
    assertThat(doc.reason()).isEqualTo(expected.reason());
    assertThat(doc.fields()).isEqualTo(expected.fields());
  }
}
