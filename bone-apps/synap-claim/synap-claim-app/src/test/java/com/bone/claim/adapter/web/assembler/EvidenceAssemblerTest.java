package com.bone.claim.adapter.web.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.controller.EvidenceController;
import com.bone.claim.adapter.web.dto.response.EvidenceResp;
import com.bone.claim.domain.model.evidence.Evidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceAssemblerTest {

  @Test
  @DisplayName("证据字段逐项映射，保留前端解析端点所需的 id")
  void mapsAllContractFields() {
    Instant now = Instant.parse("2026-11-08T10:00:00Z");
    Evidence evidence = mock(Evidence.class);
    when(evidence.getId()).thenReturn(7L);
    when(evidence.getClaimId()).thenReturn(22L);
    when(evidence.getDocType()).thenReturn("INVOICE");
    when(evidence.getFileRef()).thenReturn("oss://ref/1");
    when(evidence.getContentHash()).thenReturn("abc123");
    when(evidence.getOcrStatus()).thenReturn("DONE");
    when(evidence.getOcrConfidence()).thenReturn(new BigDecimal("0.93"));
    when(evidence.getVerifyValid()).thenReturn("PASS");
    when(evidence.getExtractedJson()).thenReturn("{}");
    when(evidence.getLocked()).thenReturn(0);
    when(evidence.getLockedAt()).thenReturn(null);
    when(evidence.getCreatedAt()).thenReturn(now);
    when(evidence.getUpdatedAt()).thenReturn(now);

    EvidenceResp resp = EvidenceAssembler.toResp(List.of(evidence)).get(0);

    assertThat(resp.id()).isEqualTo(7L);
    assertThat(resp.claimId()).isEqualTo(22L);
    assertThat(resp.docType()).isEqualTo("INVOICE");
    assertThat(resp.fileRef()).isEqualTo("oss://ref/1");
    assertThat(resp.contentHash()).isEqualTo("abc123");
    assertThat(resp.ocrStatus()).isEqualTo("DONE");
    assertThat(resp.ocrConfidence()).isEqualByComparingTo("0.93");
    assertThat(resp.verifyValid()).isEqualTo("PASS");
    assertThat(resp.locked()).isZero();
    assertThat(resp.createdAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("null 与空列表回落空列表")
  void nullAndEmpty() {
    assertThat(EvidenceAssembler.toResp(null)).isEmpty();
    assertThat(EvidenceAssembler.toResp(List.of())).isEmpty();
  }

  @Test
  @DisplayName("材料列表端点不得再直出 Evidence 聚合根（HC-003 负向钉子）")
  void controllerMustNotReturnAggregate() throws Exception {
    String generic =
        EvidenceController.class.getMethod("list", long.class).getGenericReturnType().getTypeName();
    assertThat(generic).doesNotContain("domain.model.evidence.Evidence");
    assertThat(generic).contains("EvidenceResp");
  }
}
