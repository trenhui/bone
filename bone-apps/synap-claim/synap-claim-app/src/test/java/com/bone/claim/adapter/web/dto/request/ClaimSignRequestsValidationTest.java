package com.bone.claim.adapter.web.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 签收金额边界契约：负数必须在进入领域前被拒，不能一路流到建案/理算。 */
class ClaimSignRequestsValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("顶层赔案金额为负 → 校验失败")
  void rejectsNegativeTopLevelAmount() {
    ClaimSignRequests.AcknowledgeSignReq req =
        new ClaimSignRequests.AcknowledgeSignReq("biz-1", "API_PUSH", "CL001", -100L, List.of());

    assertThat(messages(validator.validate(req))).contains("赔案金额不能为负");
  }

  @Test
  @DisplayName("明细赔案金额为负 → 级联校验失败")
  void rejectsNegativeCaseElementAmount() {
    ClaimSignRequests.SignCaseElementReq element =
        new ClaimSignRequests.SignCaseElementReq(
            "CL001", "P001", 1L, "张三", "110101199001010017", null, "OUTPATIENT", "API_PUSH", -1L);
    ClaimSignRequests.AcknowledgeSignReq req =
        new ClaimSignRequests.AcknowledgeSignReq("biz-2", "API_PUSH", null, null, List.of(element));

    assertThat(messages(validator.validate(req))).contains("赔案金额不能为负");
  }

  @Test
  @DisplayName("金额为 0 或空 → 合法（未知金额仍按既有人工兜底口径）")
  void acceptsZeroAndNullAmount() {
    ClaimSignRequests.AcknowledgeSignReq zero =
        new ClaimSignRequests.AcknowledgeSignReq("biz-3", "API_PUSH", "CL003", 0L, List.of());
    ClaimSignRequests.AcknowledgeSignReq unknown =
        new ClaimSignRequests.AcknowledgeSignReq("biz-4", "API_PUSH", null, null, List.of());

    assertThat(validator.validate(zero)).isEmpty();
    assertThat(validator.validate(unknown)).isEmpty();
  }

  private static Set<String> messages(
      Set<ConstraintViolation<ClaimSignRequests.AcknowledgeSignReq>> violations) {
    return violations.stream()
        .map(ConstraintViolation::getMessage)
        .collect(java.util.stream.Collectors.toSet());
  }
}
