package com.bone.claim.domain.security;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM 输出结构化校验（AI 安全三件套之三，§11.3 / AI 技术方案 §6.4）。
 *
 * <p>不得信任模型输出形态：① 字段白名单（只接受预定义字段，多余字段丢弃并告警）；② 与 OCR 结果交叉验证 （LLM 抽取的金额/日期/医院与 OCR 字段比对，不一致以 OCR
 * 为准并标记冲突——AI 不碰已确定事实，与 P1 同源）； ③ 必填校验。模型输出只是建议，确定性抽取结果优先。
 *
 * <p>纯 Java 不依赖 JSON 库：入参为已解析的 {@code Map<String,Object>}，由应用层（Jackson）在调用前解析。
 */
public final class LlmOutputGuard {

  private LlmOutputGuard() {}

  /** 字段冲突（LLM 抽取值 vs OCR 值不一致）。 */
  public record Conflict(String field, Object modelValue, Object ocrValue) {}

  /** 字段白名单过滤：丢弃白名单外字段，返回保留后的副本。 */
  public static Map<String, Object> filterWhitelist(
      Map<String, Object> modelOutput, Set<String> allowedFields) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (modelOutput == null) {
      return result;
    }
    for (Map.Entry<String, Object> e : modelOutput.entrySet()) {
      if (allowedFields.contains(e.getKey())) {
        result.put(e.getKey(), e.getValue());
      }
    }
    return result;
  }

  /** 必填校验：返回缺失的字段名列表（空表示齐备）。 */
  public static List<String> requireFields(
      Map<String, Object> modelOutput, Set<String> requiredFields) {
    List<String> missing = new ArrayList<>();
    if (modelOutput == null) {
      missing.addAll(requiredFields);
      return missing;
    }
    for (String f : requiredFields) {
      Object v = modelOutput.get(f);
      if (v == null || (v instanceof String && ((String) v).isBlank())) {
        missing.add(f);
      }
    }
    return missing;
  }

  /** 与 OCR 结果交叉验证：对指定字段逐一比对，返回冲突列表（空表示一致）。 数值按 {@code BigDecimal} 归一比较（容差 0），非数值按字符串相等比较。 */
  public static List<Conflict> crossCheck(
      Map<String, Object> modelOutput, Map<String, Object> ocr, List<String> fields) {
    List<Conflict> conflicts = new ArrayList<>();
    if (modelOutput == null || ocr == null) {
      return conflicts;
    }
    for (String f : fields) {
      Object mv = modelOutput.get(f);
      Object ov = ocr.get(f);
      if (!valuesEqual(mv, ov)) {
        conflicts.add(new Conflict(f, mv, ov));
      }
    }
    return conflicts;
  }

  private static boolean valuesEqual(Object a, Object b) {
    if (a == null && b == null) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    BigDecimal ba = toDecimal(a);
    BigDecimal bb = toDecimal(b);
    if (ba != null && bb != null) {
      return ba.compareTo(bb) == 0;
    }
    return String.valueOf(a).trim().equals(String.valueOf(b).trim());
  }

  private static BigDecimal toDecimal(Object v) {
    if (v instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    if (v instanceof String s && !s.isBlank()) {
      try {
        return new BigDecimal(s.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
