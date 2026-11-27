package com.bone.claim.domain.model.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 存量材料重新分类：只改类型、锁定后拒绝，且 OCR/验真/清晰度不受污染。 */
class EvidenceReclassifyTest {

  private static Evidence evidence() {
    return Evidence.register(7L, 42L, 1001L, DocType.LIST, "oss://ref/1", "hash-1", "UNKNOWN");
  }

  @Test
  @DisplayName("重新归类只改 docType，不动解析/验真/清晰度")
  void reclassifyChangesOnlyDocType() {
    Evidence evidence = evidence();
    String clearType = evidence.getClearType();
    String ocrStatus = evidence.getOcrStatus();

    evidence.reclassify(DocType.ID_CARD);

    assertThat(evidence.docTypeEnum()).isEqualTo(DocType.ID_CARD);
    assertThat(evidence.getClearType()).isEqualTo(clearType);
    assertThat(evidence.getOcrStatus()).isEqualTo(ocrStatus);
    assertThat(evidence.getVerifyValid()).isEqualTo("UNKNOWN");
  }

  @Test
  @DisplayName("锁定后重新归类被拒：证据集锁定后类型不可变")
  void reclassifyAfterLockRejected() {
    Evidence evidence = evidence();
    evidence.lock("op-evidence", 1);

    assertThatThrownBy(() -> evidence.reclassify(DocType.BANK_CARD))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("证据已锁定，禁止修改材料类型");
    assertThat(evidence.docTypeEnum()).isEqualTo(DocType.LIST);
  }

  @Test
  @DisplayName("空类型被拒：不得把 docType 写成 null 损坏值域")
  void nullDocTypeRejected() {
    assertThatThrownBy(() -> evidence().reclassify(null))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("材料类型不能为空");
  }
}
