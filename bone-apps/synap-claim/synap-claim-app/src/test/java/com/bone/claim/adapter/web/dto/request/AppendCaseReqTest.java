package com.bone.claim.adapter.web.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 追加赔案请求的「未知字段」契约测试。
 *
 * <p><b>为什么要单独测一个 DTO</b>：Spring Boot 默认关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES}，
 * 「请求里没声明的字段」会被无声丢弃。对普通 DTO 这是宽容，对本 DTO 是漏洞——调用方传了 {@code policyNo}，会以为覆盖成功、实际被忽略，赔付安静地挂到报案自己的保单上。
 * 本类钉死：保单维度的键名一旦出现就被捕获并点名返回；其他无关未知字段不误伤。
 */
class AppendCaseReqTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private AppendCaseReq parse(String json) throws Exception {
    return objectMapper.readValue(json, AppendCaseReq.class);
  }

  @Test
  @DisplayName("只带赔付维度字段 → 无越权键，且字段正常绑定")
  void acceptsPayOnlyPayload() throws Exception {
    AppendCaseReq req =
        parse(
            """
            {
              "claimType": "OUTPATIENT",
              "invoiceCode": "04400001",
              "invoiceNo": "12345678",
              "hospitalCode": "H001",
              "items": [{"itemName": "诊查费", "category": "SERVICE", "amount": 10.00}]
            }
            """);

    assertThat(req.forbiddenContextKeys()).isEmpty();
    assertThat(req.getClaimType()).isEqualTo("OUTPATIENT");
    assertThat(req.getInvoiceNo()).isEqualTo("12345678");
    assertThat(req.getItems()).hasSize(1);
  }

  @Test
  @DisplayName("传 policyNo → 被捕获并点名（不静默丢弃）")
  void rejectsPolicyNo() throws Exception {
    AppendCaseReq req =
        parse(
            """
            {"policyNo": "EG-HIJACK-9999", "items": [{"itemName": "诊查费", "amount": 10.00}]}
            """);

    assertThat(req.forbiddenContextKeys()).containsExactly("policyNo");
    // 该字段确实没有被绑定到任何属性上（结构性保证：DTS 里根本没有这个字段）
    assertThat(req.getClaimType()).isNull();
  }

  @Test
  @DisplayName("同时传多个保单维度字段 → 全部点名（错误信息要能一次说清）")
  void listsAllContextOverrides() throws Exception {
    AppendCaseReq req =
        parse(
            """
            {
              "planId": 7,
              "insuredIdHash": "hash-other",
              "outInsureTime": "2026-01-01T00:00:00Z",
              "reportId": 123,
              "items": [{"itemName": "诊查费", "amount": 10.00}]
            }
            """);

    assertThat(req.forbiddenContextKeys())
        .containsExactlyInAnyOrder("planId", "insuredIdHash", "outInsureTime", "reportId");
  }

  @Test
  @DisplayName("无关的未知字段不误伤（避免把任何拼写错误都当成越权，让用户无法自查）")
  void ignoresUnrelatedUnknownFields() throws Exception {
    AppendCaseReq req =
        parse(
            """
            {"clientRequestId": "abc", "items": [{"itemName": "诊查费", "amount": 10.00}]}
            """);

    assertThat(req.forbiddenContextKeys()).isEmpty();
  }

  @Test
  @DisplayName("空的 items 由 @NotEmpty 兜住（DTO 层不带默认明细，避免「零明细赔案」）")
  void itemsDefaultIsEmpty() throws Exception {
    AppendCaseReq req = parse("{}");

    assertThat(req.getItems()).isEmpty();
  }
}
