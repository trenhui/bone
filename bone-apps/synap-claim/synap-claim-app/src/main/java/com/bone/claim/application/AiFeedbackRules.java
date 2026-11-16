package com.bone.claim.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// AI 回流的纯校验规则：不依赖 Spring / DB，可直接在单测里做正向与负向钉子。
// 与 bone-ai-orchestration 的 ReasonType 四值逐字对齐，不得扩充。
public final class AiFeedbackRules {

  public static final Set<String> EVENT_TYPES = Set.of("IMPRESSION", "DISPOSITION");
  public static final Set<String> DISPOSITIONS = Set.of("ADOPT", "REJECT", "MODIFY");
  public static final Set<String> REASON_TYPES =
      Set.of("RULE_MISS", "CONTEXT_MISS", "SUGGESTION_WRONG", "POLICY_CONFLICT");
  public static final Set<String> INVOLVEMENTS =
      Set.of("AI_ASSISTED", "AI_STUB", "AI_UNAVAILABLE", "UNDETERMINED");
  public static final Set<String> SCENES = Set.of("case", "review");
  // 只记字段名，不记值。金额 / 决策结论字段名一律不在此集合内。
  public static final Set<String> MODIFIABLE_FIELDS = Set.of("decisionResult");
  public static final int MAX_COMMENT = 200;
  public static final int MAX_MODIFIED_FIELDS = 20;

  private static final Pattern EVENT_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
  private static final Pattern TRACE_ID = Pattern.compile("^[A-Za-z0-9_.:-]{0,128}$");

  private AiFeedbackRules() {}

  // 返回 null 表示通过；返回中文说明作为错误 detail（含后果 + 正确写法）。
  public static String validate(AiFeedbackCommand c) {
    if (c == null) {
      return "请求体为空";
    }
    if (!EVENT_ID.matcher(nvl(c.eventId())).matches()) {
      return "eventId 非法：需 8..64 位字母/数字/短横线/下划线，作为幂等键不得缺失";
    }
    if (!EVENT_TYPES.contains(nvl(c.eventType()))) {
      return "eventType 非法：只允许 IMPRESSION / DISPOSITION";
    }
    if (!INVOLVEMENTS.contains(nvl(c.involvement()))) {
      return "involvement 非法：四态不可压平，也不得编造第五态";
    }
    if (!SCENES.contains(nvl(c.scene()))) {
      return "scene 非法：blind_label 是盲标金标准禁区，禁止产生 AI 裁决回流";
    }
    if (!TRACE_ID.matcher(nvl(c.traceId())).matches()) {
      return "traceId 非法：只允许 128 位内的字母/数字/._:-，不得把用户输入拼进审计链";
    }
    Map<String, Object> extra = c.unknownFields();
    if (extra != null && !extra.isEmpty()) {
      return "含未登记字段 " + extra.keySet() + "：金额、决策结论与任何越界字段都不得进入 AI 回流（AI 不碰钱）";
    }
    if (nvl(c.comment()).length() > MAX_COMMENT) {
      return "人工备注超过 " + MAX_COMMENT + " 字符，请缩短后重试";
    }
    List<String> fields = c.modifiedFields();
    if (fields != null && fields.size() > MAX_MODIFIED_FIELDS) {
      return "modifiedFields 超过 " + MAX_MODIFIED_FIELDS + " 项";
    }
    if (fields != null) {
      for (String f : fields) {
        if (!MODIFIABLE_FIELDS.contains(nvl(f))) {
          return "modifiedFields 含未登记字段 " + f + "：只允许记录 decisionResult 字段名，金额与资金字段禁止回流";
        }
      }
    }
    if ("IMPRESSION".equals(c.eventType())) {
      return validateImpression(c, fields);
    }
    return validateDisposition(c, fields);
  }

  private static String validateImpression(AiFeedbackCommand c, List<String> fields) {
    if (c.disposition() != null
        || c.reasonCode() != null
        || (fields != null && !fields.isEmpty())) {
      return "曝光事件只能记曝光：不得夹带裁决 / 原因码 / 修改字段，否则采纳率分子被污染";
    }
    return null;
  }

  private static String validateDisposition(AiFeedbackCommand c, List<String> fields) {
    if (!"AI_ASSISTED".equals(c.involvement())) {
      return "只有 AI_ASSISTED（真实模型参与）可裁决；AI_STUB / AI_UNAVAILABLE / "
          + "UNDETERMINED 必须人工在作业页终审，不得按 AI 建议采纳率统计";
    }
    if (!DISPOSITIONS.contains(nvl(c.disposition()))) {
      return "disposition 非法：只允许 ADOPT / REJECT / MODIFY";
    }
    boolean needsReason = "REJECT".equals(c.disposition()) || "MODIFY".equals(c.disposition());
    if (needsReason && !REASON_TYPES.contains(nvl(c.reasonCode()))) {
      return "REJECT / MODIFY 必须选择受控原因码（RULE_MISS / CONTEXT_MISS / "
          + "SUGGESTION_WRONG / POLICY_CONFLICT），自由文本不能替代";
    }
    if ("MODIFY".equals(c.disposition()) && (fields == null || fields.isEmpty())) {
      return "MODIFY 必须登记被修改的字段名（只记字段名，不记值）";
    }
    if (!"MODIFY".equals(c.disposition()) && fields != null && !fields.isEmpty()) {
      return "只有 MODIFY 允许登记 modifiedFields";
    }
    if (c.reasonCode() != null && !REASON_TYPES.contains(c.reasonCode())) {
      return "未登记的原因码 " + c.reasonCode() + "：不得写进回流管道";
    }
    return null;
  }

  // 白名单字段名，拼接成 JSON 数组字符串；值永远不会进入这里。
  public static String toModifiedFieldsJson(List<String> fields) {
    if (fields == null || fields.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('"').append(fields.get(i)).append('"');
    }
    return sb.append(']').toString();
  }

  public static String nvl(String s) {
    return s == null ? "" : s;
  }
}
