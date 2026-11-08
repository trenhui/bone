package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.infrastructure.document.TextLayerDocumentParsePortAdapter;
import com.bone.core.domain.event.DomainEventPublisher;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 主动解析用例：解析产物必须真正落进证据聚合，且失败路径不被置信度逻辑覆盖。 */
@ExtendWith(MockitoExtension.class)
class EvidenceParsingTest {

  private static final long EVIDENCE_ID = 7L;
  private static final long CLAIM_ID = 1001L;

  private static final String FULL_INVOICE =
      "电子发票（普通发票）\n"
          + "发票代码：0440000123\n"
          + "发票号码：12345678\n"
          + "开票日期：2026-03-12\n"
          + "价税合计（小写）：¥1,234.56\n"
          + "购买方名称：某某\n";

  @Mock private EvidenceRepository evidenceRepository;
  @Mock private TenantPort tenantPort;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private ClaimCaseRepository claimCaseRepository;

  private EvidenceApplicationService service;
  private Evidence evidence;

  @BeforeEach
  void setUp() {
    service =
        new EvidenceApplicationService(
            evidenceRepository,
            tenantPort,
            domainEventPublisher,
            claimCaseRepository,
            new TextLayerDocumentParsePortAdapter());
    evidence =
        Evidence.register(
            EVIDENCE_ID, 42L, CLAIM_ID, DocType.INVOICE, "file-ref", "hash-1", "UNKNOWN");
    when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(evidence);
  }

  @Test
  @DisplayName("完整文本层：状态 DONE、置信度与结构化 JSON 一并落库")
  void fullTextLayerIsPersisted() {
    EvidenceApplicationService.ParseOutcome outcome = service.parse(EVIDENCE_ID, FULL_INVOICE);

    assertThat(outcome.status()).isEqualTo("DONE");
    assertThat(outcome.available()).isTrue();
    assertThat(outcome.complete()).isTrue();
    assertThat(outcome.fieldCount()).isEqualTo(4);
    assertThat(outcome.minConfidence()).isEqualByComparingTo(new BigDecimal("0.92"));
    assertThat(evidence.getOcrConfidence()).isEqualByComparingTo(new BigDecimal("0.92"));
    assertThat(evidence.getExtractedJson()).contains("invoice_code", "evidenceRef", "text@");
    verify(evidenceRepository).save(evidence);
  }

  @Test
  @DisplayName("无文本层：保持 FAILED（不是 MANUAL），置信度不得伪造")
  void missingTextLayerStaysFailed() {
    EvidenceApplicationService.ParseOutcome outcome = service.parse(EVIDENCE_ID, "  ");

    assertThat(outcome.available()).isFalse();
    assertThat(outcome.status()).isEqualTo("FAILED");
    assertThat(evidence.getOcrConfidence()).isNull();
    assertThat(evidence.getExtractedJson()).contains("TEXT_LAYER_MISSING");
  }

  @Test
  @DisplayName("缺关键字段：转人工补录，不默写缺失值")
  void incompleteParseGoesManual() {
    String missingInvoiceNo = FULL_INVOICE.replace("发票号码：12345678\n", "");

    EvidenceApplicationService.ParseOutcome outcome = service.parse(EVIDENCE_ID, missingInvoiceNo);

    assertThat(outcome.complete()).isFalse();
    assertThat(outcome.status()).isEqualTo("MANUAL");
    assertThat(evidence.getExtractedJson()).contains("INCOMPLETE_FIELDS");
  }
}
