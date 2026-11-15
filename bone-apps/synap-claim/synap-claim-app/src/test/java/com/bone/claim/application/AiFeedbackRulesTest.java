package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// AI 回流闸门的纯规则测试：每个「本该拦住」的写法都要有会红的钉子。
class AiFeedbackRulesTest {

  private static AiFeedbackCommand cmd(
      String eventType,
      String involvement,
      String scene,
      String disposition,
      String reasonCode,
      List<String> fields,
      Map<String, Object> extra) {
    return new AiFeedbackCommand(
        "evt-12345678",
        eventType,
        "trace-1",
        involvement,
        scene,
        disposition,
        reasonCode,
        null,
        fields,
        extra);
  }

  private static AiFeedbackCommand impression() {
    return cmd("IMPRESSION", "AI_ASSISTED", "case", null, null, null, Map.of());
  }

  private static AiFeedbackCommand adopt() {
    return cmd("DISPOSITION", "AI_ASSISTED", "case", "ADOPT", null, null, Map.of());
  }

  @Test
  @DisplayName("曝光事件与采纳事件均通过")
  void happyPath() {
    assertThat(AiFeedbackRules.validate(impression())).isNull();
    assertThat(AiFeedbackRules.validate(adopt())).isNull();
  }

  @Test
  @DisplayName("四个 ReasonType 全部可裁决，第五个值必须被拒")
  void reasonCodes() {
    for (String reason : AiFeedbackRules.REASON_TYPES) {
      assertThat(
              AiFeedbackRules.validate(
                  cmd("DISPOSITION", "AI_ASSISTED", "case", "REJECT", reason, null, Map.of())))
          .as(reason)
          .isNull();
    }
    assertThat(
            AiFeedbackRules.validate(
                cmd("DISPOSITION", "AI_ASSISTED", "case", "REJECT", "OTHER", null, Map.of())))
        .contains("受控原因码");
  }

  @Test
  @DisplayName("REJECT / MODIFY 缺原因码一律拒绝")
  void rejectRequiresReason() {
    for (String d : List.of("REJECT", "MODIFY")) {
      List<String> fields = "MODIFY".equals(d) ? List.of("decisionResult") : null;
      assertThat(
              AiFeedbackRules.validate(
                  cmd("DISPOSITION", "AI_ASSISTED", "case", d, null, fields, Map.of())))
          .as(d)
          .contains("原因码");
    }
  }

  @Test
  @DisplayName("非 AI_ASSISTED 三态不得产生裁决（四态不可压平）")
  void onlyRealModelCanBeDisposed() {
    for (String state : List.of("AI_STUB", "AI_UNAVAILABLE", "UNDETERMINED")) {
      assertThat(
              AiFeedbackRules.validate(
                  cmd("DISPOSITION", state, "case", "ADOPT", null, null, Map.of())))
          .as(state)
          .contains("AI_ASSISTED");
    }
  }

  @Test
  @DisplayName("盲标禁区不产生任何回流事件")
  void blindLabelZone() {
    assertThat(
            AiFeedbackRules.validate(
                cmd("IMPRESSION", "AI_ASSISTED", "blind_label", null, null, null, Map.of())))
        .contains("禁区");
  }

  @Test
  @DisplayName("未知字段（夹带金额 / 决策值）在服务端被拒，AI 不碰钱")
  void unknownFieldsRejected() {
    assertThat(
            AiFeedbackRules.validate(
                cmd(
                    "DISPOSITION",
                    "AI_ASSISTED",
                    "case",
                    "ADOPT",
                    null,
                    null,
                    Map.of("approvedAmount", 100))))
        .contains("未登记字段");
  }

  @Test
  @DisplayName("modifiedFields 只允许 decisionResult 字段名，金额字段名被拒")
  void modifiedFieldsWhitelist() {
    assertThat(
            AiFeedbackRules.validate(
                cmd(
                    "DISPOSITION",
                    "AI_ASSISTED",
                    "case",
                    "MODIFY",
                    "SUGGESTION_WRONG",
                    List.of("decisionResult"),
                    Map.of())))
        .isNull();
    assertThat(
            AiFeedbackRules.validate(
                cmd(
                    "DISPOSITION",
                    "AI_ASSISTED",
                    "case",
                    "MODIFY",
                    "SUGGESTION_WRONG",
                    List.of("approvedAmount"),
                    Map.of())))
        .contains("金额");
  }

  @Test
  @DisplayName("MODIFY 必须登记字段名；ADOPT 不得登记")
  void modifyFieldPresence() {
    assertThat(
            AiFeedbackRules.validate(
                cmd(
                    "DISPOSITION",
                    "AI_ASSISTED",
                    "case",
                    "MODIFY",
                    "SUGGESTION_WRONG",
                    null,
                    Map.of())))
        .contains("字段名");
    assertThat(
            AiFeedbackRules.validate(
                cmd(
                    "DISPOSITION",
                    "AI_ASSISTED",
                    "case",
                    "ADOPT",
                    null,
                    List.of("decisionResult"),
                    Map.of())))
        .contains("只有 MODIFY");
  }

  @Test
  @DisplayName("曝光事件夹带裁决会污染采纳率分子")
  void impressionMustStayPure() {
    assertThat(
            AiFeedbackRules.validate(
                cmd("IMPRESSION", "AI_ASSISTED", "case", "ADOPT", null, null, Map.of())))
        .contains("只能记曝光");
  }

  @Test
  @DisplayName("eventId 非法 / 缺失一律拒绝（幂等键不可省）")
  void eventIdGuard() {
    assertThat(
            AiFeedbackRules.validate(
                new AiFeedbackCommand(
                    "x",
                    "IMPRESSION",
                    "",
                    "AI_ASSISTED",
                    "case",
                    null,
                    null,
                    null,
                    null,
                    Map.of())))
        .contains("eventId");
    assertThat(AiFeedbackRules.validate(null)).contains("请求体为空");
  }

  @Test
  @DisplayName("traceId 不得夹带注入字符")
  void traceIdGuard() {
    assertThat(
            AiFeedbackRules.validate(
                new AiFeedbackCommand(
                    "evt-12345678",
                    "IMPRESSION",
                    "a' OR 1=1",
                    "AI_ASSISTED",
                    "case",
                    null,
                    null,
                    null,
                    null,
                    Map.of())))
        .contains("traceId");
  }

  @Test
  @DisplayName("备注超长拒绝；toModifiedFieldsJson 只含字段名不含值")
  void commentAndJson() {
    assertThat(
            AiFeedbackRules.validate(
                new AiFeedbackCommand(
                    "evt-12345678",
                    "IMPRESSION",
                    "",
                    "AI_ASSISTED",
                    "case",
                    null,
                    null,
                    "x".repeat(201),
                    null,
                    Map.of())))
        .contains("超过");
    assertThat(AiFeedbackRules.toModifiedFieldsJson(List.of("decisionResult")))
        .isEqualTo("[\"decisionResult\"]")
        .doesNotContain("100", "amount");
    assertThat(AiFeedbackRules.toModifiedFieldsJson(null)).isNull();
  }
}
