package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.InvoiceVerificationPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.infrastructure.invoice.ControlledInvoiceVerificationPortAdapter;
import com.bone.core.domain.event.DomainEventPublisher;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 验真/验重前移：查实问题才 FAIL，本地核验通过绝不冒充税局 PASS。 */
@ExtendWith(MockitoExtension.class)
class InvoiceVerificationTest {

  private static final long EVIDENCE_ID = 7L;
  private static final long CLAIM_ID = 1001L;
  private static final long TENANT_ID = 42L;

  @Mock private EvidenceRepository evidenceRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private DomainEventPublisher domainEventPublisher;

  private InvoiceVerificationApplicationService service;
  private Evidence evidence;

  @BeforeEach
  void setUp() {
    service =
        new InvoiceVerificationApplicationService(
            evidenceRepository,
            new ControlledInvoiceVerificationPortAdapter(claimCaseRepository),
            domainEventPublisher);
    evidence =
        Evidence.register(
            EVIDENCE_ID, TENANT_ID, CLAIM_ID, DocType.INVOICE, "file-ref", "hash-1", "UNKNOWN");
    evidence.markParsed(
        OcrStatus.DONE, new BigDecimal("0.92"), fields("0440000123", "12345678", "300.00"), null);
    when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(evidence);
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of(evidence));
  }

  @Test
  @DisplayName("本地核验通过但无税局通道：UNVERIFIABLE 转人工，绝不落 PASS")
  void localCheckNeverClaimsVerified() {
    InvoiceVerificationPort.VerificationResult result = service.verify(EVIDENCE_ID);

    assertThat(result.available()).isTrue();
    assertThat(result.verifyValid()).isEqualTo("UNVERIFIABLE");
    assertThat(result.reason())
        .isEqualTo(ControlledInvoiceVerificationPortAdapter.REASON_TAX_CHANNEL_MISSING);
    assertThat(result.channel()).isEqualTo(ControlledInvoiceVerificationPortAdapter.CHANNEL);
    assertThat(evidence.isVerifyPassed()).isFalse();
  }

  @Test
  @DisplayName("发票代码格式非法：前置落 FAIL，进入 R-FRAUD-06 拦截")
  void invalidInvoiceCodeFails() {
    evidence.markParsed(
        OcrStatus.DONE, new BigDecimal("0.92"), fields("ABC", "12345678", "300.00"), null);

    InvoiceVerificationPort.VerificationResult result = service.verify(EVIDENCE_ID);

    assertThat(result.verifyValid()).isEqualTo("FAIL");
    assertThat(result.reason()).isEqualTo("INVOICE_CODE_FORMAT_INVALID");
    assertThat(evidence.isVerifyPassed()).isFalse();
  }

  @Test
  @DisplayName("发票金额与清单合计差额 >1 元：R-FRAUD-04 在材料阶段命中")
  void amountMismatchFails() {
    Evidence list =
        Evidence.register(8L, TENANT_ID, CLAIM_ID, DocType.LIST, "list-ref", "hash-2", "UNKNOWN");
    list.markParsed(
        OcrStatus.DONE,
        new BigDecimal("0.92"),
        "{\"fields\":[{\"code\":\"total_amount\",\"value\":\"250.00\"}]}",
        null);
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of(evidence, list));

    InvoiceVerificationPort.VerificationResult result = service.verify(EVIDENCE_ID);

    assertThat(result.verifyValid()).isEqualTo("FAIL");
    assertThat(result.reason()).isEqualTo("INVOICE_ITEM_AMOUNT_MISMATCH");
  }

  @Test
  @DisplayName("跨案重复发票：R-FRAUD-07 提前到材料阶段，不再等建单 409")
  void duplicateAcrossClaimsFails() {
    ClaimCase other = org.mockito.Mockito.mock(ClaimCase.class);
    when(other.getId()).thenReturn(9999L);
    when(claimCaseRepository.findByInvoice(TENANT_ID, "0440000123", "12345678"))
        .thenReturn(List.of(other));

    InvoiceVerificationPort.VerificationResult result = service.verify(EVIDENCE_ID);

    assertThat(result.verifyValid()).isEqualTo("FAIL");
    assertThat(result.reason()).isEqualTo("DUPLICATE_INVOICE");
  }

  @Test
  @DisplayName("本案自身的发票行不算重复（排除自身）")
  void ownClaimIsNotDuplicate() {
    ClaimCase self = org.mockito.Mockito.mock(ClaimCase.class);
    when(self.getId()).thenReturn(CLAIM_ID);
    when(claimCaseRepository.findByInvoice(TENANT_ID, "0440000123", "12345678"))
        .thenReturn(List.of(self));

    assertThat(service.verify(EVIDENCE_ID).verifyValid()).isEqualTo("UNVERIFIABLE");
  }

  @Test
  @DisplayName("缺关键字段：available=false，不默写票号也不判通过")
  void missingInvoiceKeyFailsClosed() {
    evidence.markParsed(OcrStatus.DONE, new BigDecimal("0.92"), "{\"fields\":[]}", null);

    InvoiceVerificationPort.VerificationResult result = service.verify(EVIDENCE_ID);

    assertThat(result.available()).isFalse();
    assertThat(result.reason()).isEqualTo("INVOICE_KEY_MISSING");
    assertThat(result.verifyValid()).isEqualTo("UNKNOWN");
  }

  @Test
  @DisplayName("验真回填不重置解析状态/置信度，且锁定后拒绝写入")
  void markVerifiedIsOrthogonalToParsingState() {
    service.verify(EVIDENCE_ID);

    assertThat(evidence.ocrStatusEnum()).isEqualTo(OcrStatus.DONE);
    assertThat(evidence.getOcrConfidence()).isEqualByComparingTo("0.92");
    evidence.lock("op-1", 1);
    assertThatThrownBy(() -> evidence.markVerified("PASS")).hasMessageContaining("已锁定");
  }

  private static String fields(String code, String no, String amount) {
    return "{\"fields\":["
        + "{\"code\":\"invoice_code\",\"value\":\""
        + code
        + "\"},"
        + "{\"code\":\"invoice_no\",\"value\":\""
        + no
        + "\"},"
        + "{\"code\":\"total_amount\",\"value\":\""
        + amount
        + "\"}]}";
  }
}
