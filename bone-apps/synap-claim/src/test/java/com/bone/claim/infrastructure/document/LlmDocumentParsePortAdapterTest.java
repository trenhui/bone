package com.bone.claim.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.claim.application.port.out.DocumentParsePort;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 模型驱动感知：产物合规才可用，替身/异常/越界一律 fail-closed，冲突以确定性基线为准。 */
@ExtendWith(MockitoExtension.class)
class LlmDocumentParsePortAdapterTest {

  private static final String INVOICE_TEXT =
      "电子发票（普通发票）\n"
          + "发票代码：0440000123\n"
          + "发票号码：12345678\n"
          + "开票日期：2026-03-12\n"
          + "价税合计（小写）：¥1,234.56\n";

  @Mock private LlmGatewayApplicationService gateway;

  private LlmDocumentParsePortAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new LlmDocumentParsePortAdapter(gateway);
  }

  @Test
  @DisplayName("模型抽取合规：available/complete 成立，字段带模型证据定位")
  void validModelExtraction() {
    givenResponse(
        "{\"fields\":["
            + "{\"code\":\"invoice_code\",\"value\":\"0440000123\",\"confidence\":0.96},"
            + "{\"code\":\"invoice_no\",\"value\":\"12345678\",\"confidence\":0.96},"
            + "{\"code\":\"invoice_date\",\"value\":\"2026-03-12\",\"confidence\":0.92},"
            + "{\"code\":\"total_amount\",\"value\":\"1234.56\",\"confidence\":0.95}]}");

    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, INVOICE_TEXT);

    assertThat(doc.available()).isTrue();
    assertThat(doc.complete()).isTrue();
    assertThat(doc.reason()).isEqualTo(LlmDocumentParsePortAdapter.REASON_OK);
    assertThat(doc.fields()).hasSize(4);
    assertThat(doc.fields().get(0).evidenceRef()).startsWith("llm@");
    assertThat(doc.extractedJson()).contains("LLM_WITH_DETERMINISTIC_BASELINE");
  }

  @Test
  @DisplayName("Mock 替身响应显式拒绝，不拿替身产物做解析")
  void mockResponseIsRejected() {
    givenResponse("{\"mock\":true,\"model\":\"mock\",\"echo\":\"MOCK\"}");

    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, INVOICE_TEXT);

    assertThat(doc.available()).isFalse();
    assertThat(doc.reason()).isEqualTo(LlmDocumentParsePortAdapter.REASON_MOCK_REJECTED);
  }

  @Test
  @DisplayName("网关异常：fail-closed 转人工，不伪造字段")
  void gatewayFailureFailsClosed() {
    when(gateway.invoke(any())).thenThrow(new IllegalStateException("provider down"));

    assertThat(adapter.parse(DocType.INVOICE, INVOICE_TEXT).reason())
        .isEqualTo(LlmDocumentParsePortAdapter.REASON_LLM_UNAVAILABLE);
  }

  @Test
  @DisplayName("非 JSON / 结构不符：拒绝解析结果")
  void nonJsonResponseFailsClosed() {
    givenResponse("NOT_JSON");

    assertThat(adapter.parse(DocType.INVOICE, INVOICE_TEXT).reason())
        .isEqualTo(LlmDocumentParsePortAdapter.REASON_OUTPUT_INVALID);
  }

  @Test
  @DisplayName("白名单外字段被丢弃；全越界输出视为不可用")
  void whitelistFiltersModelOutput() {
    givenResponse(
        "{\"fields\":["
            + "{\"code\":\"invoice_no\",\"value\":\"12345678\",\"confidence\":0.9},"
            + "{\"code\":\"MADE_UP_CODE\",\"value\":\"x\",\"confidence\":0.9}]}");

    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, INVOICE_TEXT);

    assertThat(doc.available()).isTrue();
    assertThat(doc.fields())
        .extracting(DocumentParsePort.Field::code)
        .containsExactly("invoice_no");
    assertThat(doc.complete()).isFalse();
  }

  @Test
  @DisplayName("与确定性基线冲突：以基线为准、降置信度并留冲突痕迹（AI 不碰已确定事实）")
  void conflictPrefersDeterministicBaseline() {
    givenResponse(
        "{\"fields\":[{\"code\":\"invoice_no\",\"value\":\"99999999\",\"confidence\":0.99}]}");

    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, INVOICE_TEXT);

    assertThat(doc.reason()).isEqualTo(LlmDocumentParsePortAdapter.REASON_CONFLICT);
    assertThat(doc.fields().get(0).value()).isEqualTo("12345678");
    assertThat(doc.fields().get(0).confidence()).isEqualByComparingTo(new BigDecimal("0.50"));
    assertThat(doc.fields().get(0).evidenceRef()).isEqualTo("baseline@invoice_no");
    assertThat(doc.extractedJson()).contains("invoice_no");
  }

  @Test
  @DisplayName("出站前 PII 遮蔽 + 数据槽位：发给网关的文本不含证件原文")
  void piiIsMaskedBeforeGatewayCall() {
    givenResponse(
        "{\"fields\":[{\"code\":\"invoice_no\",\"value\":\"12345678\",\"confidence\":0.9}]}");
    String withPii = INVOICE_TEXT + "身份证号：110101199001011234\n";

    adapter.parse(DocType.INVOICE, withPii);

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(gateway).invoke(captor.capture());
    String userPrompt =
        captor.getValue().messages().get(captor.getValue().messages().size() - 1).content();
    assertThat(userPrompt).doesNotContain("110101199001011234");
    assertThat(userPrompt).contains("<material>", "</material>");
  }

  @Test
  @DisplayName("空文本不调模型（省钱），直接转人工")
  void blankTextSkipsGateway() {
    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, "  ");

    assertThat(doc.available()).isFalse();
    assertThat(doc.reason()).isEqualTo(TextLayerDocumentParsePortAdapter.REASON_TEXT_LAYER_MISSING);
    verify(gateway, never()).invoke(any());
  }

  private void givenResponse(String content) {
    when(gateway.invoke(any()))
        .thenReturn(
            new LlmResponse(content, LlmDocumentParsePortAdapter.MODEL, "stop", LlmUsage.empty()));
  }
}
