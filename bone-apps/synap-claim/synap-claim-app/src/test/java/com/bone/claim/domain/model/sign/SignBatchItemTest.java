package com.bone.claim.domain.model.sign;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignBatchItemTest {

  @Test
  @DisplayName("携带建案要素的明细：默认待建案，关联后写入案件ID与结果状态")
  void withCaseElementsAndLinkCase() {
    SignBatchItem item =
        SignBatchItem.withCaseElements(
            10L,
            77L,
            "CL-TPA-001",
            "pk-tpa-sign-77-CL-TPA-001",
            "P-2026-001",
            0L,
            "张三",
            "110101199001011234",
            Instant.parse("2026-01-02T03:04:05Z"),
            "OUTPATIENT",
            "TPA_PUSH");

    assertThat(item.needsCaseCreation()).isTrue();
    assertThat(item.getInsuredIdNo()).isEqualTo("110101199001011234");
    assertThat(item.getCaseStatus()).isEqualTo(SignBatchItem.CASE_PENDING);

    item.linkCase(555L, SignBatchItem.CASE_NEW);

    assertThat(item.getClaimId()).isEqualTo(555L);
    assertThat(item.getCreatedCaseId()).isEqualTo(555L);
    assertThat(item.getCaseStatus()).isEqualTo(SignBatchItem.CASE_NEW);
    assertThat(item.needsCaseCreation()).isFalse();
  }

  @Test
  @DisplayName("引用已有案件的明细：不进入自动建案路径")
  void linkedItemDoesNotNeedCaseCreation() {
    SignBatchItem item = SignBatchItem.of(10L, 77L, 321L, "CL-EXISTING");

    assertThat(item.needsCaseCreation()).isFalse();
    assertThat(item.getCaseStatus()).isEqualTo(SignBatchItem.CASE_SKIPPED);
    assertThat(item.getBusinessKey()).isNull();
    assertThat(SignBatchItem.of(11L, 78L, 321L, "CL-EXISTING").getBusinessKey()).isNull();
  }
}
