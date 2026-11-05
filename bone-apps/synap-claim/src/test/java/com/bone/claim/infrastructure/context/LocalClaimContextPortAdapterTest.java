package com.bone.claim.infrastructure.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.ClaimContextPort.EvidenceFact;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.EvidenceFactPolicy;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 案件上下文适配器：材料解析事实必须真的被带到认知层，且带得"对"。
 *
 * <p><b>这组用例守的是输入侧断链</b>：感知层早已抽出带 {@code text@67:8} 定位的票面/临床字段，但在通往 AI 的这段 被丢掉了——模型只能复述聚合标量（实测
 * {@code aiFacts} 的 code 就是 {@code claimType}/{@code claimAmount}）。 只断言"有事实"不够，必须逐字段断言 code / value
 * / 定位，否则把定位丢掉也会全绿。
 *
 * <p><b>同时钉住边界</b>：姓名/证件类字段不得随事实出域。这条与上面的正向断言是一对——只测"过滤生效"会把实现推向 恒空列表（AI 看不到材料，且静默），只测"事实存在"又会漏掉出域。
 */
@ExtendWith(MockitoExtension.class)
class LocalClaimContextPortAdapterTest {

  private static final long CLAIM_ID = 1001L;
  private static final long TENANT_ID = 42L;

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private EvidenceRepository evidenceRepository;

  private LocalClaimContextPortAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new LocalClaimContextPortAdapter(claimCaseRepository, evidenceRepository);
    ClaimCase claim = mock(ClaimCase.class);
    lenient().when(claim.getClaimType()).thenReturn("OUTPATIENT");
    lenient().when(claim.claimAmount()).thenReturn(new BigDecimal("100.00"));
    lenient().when(claimCaseRepository.findById(CLAIM_ID)).thenReturn(claim);
  }

  @Test
  @DisplayName("票面事实原样带上：code / value / 原文定位一个都不能少（少了定位，模型引用无出处，系统也无法校验）")
  void invoiceFactsAreCarriedWithLocators() {
    givenEvidence(
        DocType.INVOICE,
        "{\"fields\":["
            + "{\"code\":\"invoice_no\",\"value\":\"12345678\",\"confidence\":0.96,"
            + "\"evidenceRef\":\"text@32:8\"},"
            + "{\"code\":\"total_amount\",\"value\":\"1234.56\",\"confidence\":0.95,"
            + "\"evidenceRef\":\"text@67:8\"}]}");

    List<EvidenceFact> facts = adapter.getContext(CLAIM_ID).evidenceFacts();

    assertThat(facts)
        .containsExactly(
            new EvidenceFact("INVOICE", "invoice_no", "12345678", "text@32:8"),
            new EvidenceFact("INVOICE", "total_amount", "1234.56", "text@67:8"));
  }

  @Test
  @DisplayName("身份类字段不出域，临床字段照常带上（两个方向都断，缺一个都会把实现推向极端）")
  void identityFieldsAreStrippedButClinicalFactsSurvive() {
    givenEvidence(
        DocType.MEDICAL_RECORD,
        "{\"fields\":["
            + "{\"code\":\"patient_name\",\"value\":\"张三\",\"evidenceRef\":\"text@1:3\"},"
            + "{\"code\":\"id_card\",\"value\":\"110101199001011234\",\"evidenceRef\":\"text@4:18\"},"
            + "{\"code\":\"diagnosis_code\",\"value\":\"J06.9\",\"evidenceRef\":\"text@30:5\"}]}");

    List<EvidenceFact> facts = adapter.getContext(CLAIM_ID).evidenceFacts();

    assertThat(facts).extracting(EvidenceFact::code).containsExactly("diagnosis_code");
    assertThat(facts).noneMatch(f -> f.value().contains("张三"));
  }

  @Test
  @DisplayName("空值字段跳过：空事实既不能核验也不能展示，带进去只会占提示词")
  void blankValuesAreSkipped() {
    givenEvidence(
        DocType.INVOICE,
        "{\"fields\":[{\"code\":\"invoice_no\",\"value\":\"  \",\"evidenceRef\":\"text@1:1\"},"
            + "{\"code\":\"total_amount\",\"value\":\"88.00\",\"evidenceRef\":\"text@9:5\"}]}");

    assertThat(adapter.getContext(CLAIM_ID).evidenceFacts())
        .extracting(EvidenceFact::code)
        .containsExactly("total_amount");
  }

  @Test
  @DisplayName("坏 JSON / 缺 fields 键不抛异常：决策图不能因为一条脏数据整体失败")
  void malformedExtractionYieldsNoFacts() {
    givenEvidence(DocType.INVOICE, "not-json");

    assertThat(adapter.getContext(CLAIM_ID).evidenceFacts()).isEmpty();
  }

  @Test
  @DisplayName("条数封顶：材料多的案件不能把提示词撑爆")
  void factsAreCapped() {
    StringBuilder json = new StringBuilder("{\"fields\":[");
    for (int i = 0; i < EvidenceFactPolicy.MAX_FACTS + 10; i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append("{\"code\":\"f").append(i).append("\",\"value\":\"v").append(i).append("\"}");
    }
    json.append("]}");
    givenEvidence(DocType.INVOICE, json.toString());

    assertThat(adapter.getContext(CLAIM_ID).evidenceFacts()).hasSize(EvidenceFactPolicy.MAX_FACTS);
  }

  @Test
  @DisplayName("超长值截断：OCR 偶发把整段文本塞进一个字段，截断后仍可作为事实")
  void longValuesAreTruncated() {
    String longValue = "长".repeat(EvidenceFactPolicy.MAX_VALUE_LENGTH + 30);
    givenEvidence(
        DocType.INVOICE, "{\"fields\":[{\"code\":\"remark\",\"value\":\"" + longValue + "\"}]}");

    List<EvidenceFact> facts = adapter.getContext(CLAIM_ID).evidenceFacts();

    assertThat(facts).hasSize(1);
    assertThat(facts.get(0).value()).hasSize(EvidenceFactPolicy.MAX_VALUE_LENGTH);
  }

  @Test
  @DisplayName("无材料时事实列表为空（合法状态，不是异常）：模型据此不得产出任何事实")
  void noEvidenceMeansNoFacts() {
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of());

    ClaimContextPort.ClaimContextView view = adapter.getContext(CLAIM_ID);

    assertThat(view.hasEvidence()).isFalse();
    assertThat(view.evidenceFacts()).isEmpty();
  }

  private void givenEvidence(DocType docType, String extractedJson) {
    Evidence evidence =
        Evidence.register(7L, TENANT_ID, CLAIM_ID, docType, "file-ref", "hash-1", "UNKNOWN");
    evidence.markParsed(OcrStatus.DONE, new BigDecimal("0.92"), extractedJson, null);
    List<Evidence> evidences = new ArrayList<>();
    evidences.add(evidence);
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(evidences);
  }
}
