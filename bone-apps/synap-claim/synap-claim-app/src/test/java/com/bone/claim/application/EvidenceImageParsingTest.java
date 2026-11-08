package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.DocumentParsePort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 图像件解析入口：状态映射与文本层一致；归因码原样透传（参与口径如实反映）。 */
class EvidenceImageParsingTest {

  private EvidenceRepository evidenceRepository;
  private DocumentParsePort documentParsePort;
  private EvidenceApplicationService service;

  @BeforeEach
  void setUp() {
    evidenceRepository = mock(EvidenceRepository.class);
    documentParsePort = mock(DocumentParsePort.class);
    service =
        new EvidenceApplicationService(
            evidenceRepository,
            mock(TenantPort.class),
            mock(DomainEventPublisher.class),
            mock(ClaimCaseRepository.class),
            documentParsePort);
  }

  private static Evidence imageEvidence() {
    return Evidence.register(
        1L, 42L, 9001L, DocType.MEDICAL_RECORD, "oss://bucket/x.jpg", "hash-x", null);
  }

  @Test
  @DisplayName("门控关（缺省 IMAGE_NOT_SUPPORTED）：落 FAILED 转人工，不冒充解析成功")
  void gateOffFallsToFailed() {
    Evidence evidence = imageEvidence();
    when(evidenceRepository.findById(1L)).thenReturn(evidence);
    when(documentParsePort.parseImage(any(), any()))
        .thenReturn(
            new DocumentParsePort.ParsedDocument(
                false,
                false,
                BigDecimal.ZERO,
                List.of(),
                "{\"available\":false,\"reason\":\"IMAGE_NOT_SUPPORTED\"}",
                "IMAGE_NOT_SUPPORTED"));

    var outcome = service.parseImage(1L);

    assertThat(outcome.status()).isEqualTo("FAILED");
    assertThat(outcome.available()).isFalse();
    assertThat(outcome.reason()).isEqualTo("IMAGE_NOT_SUPPORTED");
    verify(evidenceRepository).save(evidence);
  }

  @Test
  @DisplayName("成功路径：DONE + minConfidence + 归因码 IMAGE_PARSE_OK 原样透传")
  void successMapsToDone() {
    Evidence evidence = imageEvidence();
    when(evidenceRepository.findById(1L)).thenReturn(evidence);
    when(documentParsePort.parseImage(any(), any()))
        .thenReturn(
            new DocumentParsePort.ParsedDocument(
                true,
                true,
                new BigDecimal("0.88"),
                List.of(
                    new DocumentParsePort.Field(
                        "diagnosis_code", "J06.9", new BigDecimal("0.88"), "image@diagnosis_code")),
                "{}",
                "IMAGE_PARSE_OK"));

    var outcome = service.parseImage(1L);

    assertThat(outcome.status()).isEqualTo("DONE");
    assertThat(outcome.available()).isTrue();
    assertThat(outcome.minConfidence()).isEqualByComparingTo("0.88");
    assertThat(outcome.reason()).isEqualTo("IMAGE_PARSE_OK");
  }

  @Test
  @DisplayName("降级归因码（mock 拒绝/网关不可用/输出无效）逐字透传，不重写为通用码")
  void degradeCodesPassThrough() {
    for (String code :
        List.of(
            ImageDocumentParseCode.IMAGE_MOCK_REJECTED,
            ImageDocumentParseCode.IMAGE_GATEWAY_UNAVAILABLE,
            ImageDocumentParseCode.IMAGE_OUTPUT_INVALID)) {
      Evidence evidence = imageEvidence();
      when(evidenceRepository.findById(anyLong())).thenReturn(evidence);
      when(documentParsePort.parseImage(any(), any()))
          .thenReturn(
              new DocumentParsePort.ParsedDocument(
                  false, false, BigDecimal.ZERO, List.of(), "{\"reason\":\"" + code + "\"}", code));

      var outcome = service.parseImage(1L);

      assertThat(outcome.status()).isEqualTo("FAILED");
      assertThat(outcome.reason()).isEqualTo(code);
    }
  }

  /** 与 ImageDocumentParsePortAdapter 常量对齐（避免测试硬编码漂移）。 */
  private static final class ImageDocumentParseCode {
    static final String IMAGE_MOCK_REJECTED = "IMAGE_MOCK_REJECTED";
    static final String IMAGE_GATEWAY_UNAVAILABLE = "IMAGE_GATEWAY_UNAVAILABLE";
    static final String IMAGE_OUTPUT_INVALID = "IMAGE_OUTPUT_INVALID";
  }
}
