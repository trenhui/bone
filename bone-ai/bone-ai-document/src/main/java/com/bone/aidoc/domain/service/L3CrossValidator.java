package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.RecognizedField;
import java.util.HashMap;
import java.util.Map;

/**
 * L3 多模型交叉校验（ADR-0046 §四：多模型一致 ≠ 正确，但不一致必须升级）。
 *
 * <p>以主引擎识别结果为基准，逐字段与次引擎比对：两者均识别到且归一化相等 → {@link CrossCheck#MATCH}； 均识别到但不等 → {@link
 * CrossCheck#MISMATCH}（直接升级人工复核，不自动放行）； 仅主引擎识别到（无共识基准）→ {@link CrossCheck#NOT_RUN}。
 *
 * <p>归一化：金额按数值比较（容差 0.005 元），其余按去空格后大小写不敏感比较。 该工具为纯函数，不依赖任何模型 SDK，便于单测与离线回放。
 */
public final class L3CrossValidator {

  private static final double TOLERANCE = 0.005;

  private L3CrossValidator() {}

  /** 主/次引擎字段级交叉校验，返回 字段名 → 交叉结论（仅含主引擎识别到的字段）。 */
  public static Map<String, CrossCheck> compare(OcrResult primary, OcrResult secondary) {
    Map<String, String> sec = new HashMap<>();
    if (secondary != null && secondary.fields() != null) {
      for (RecognizedField f : secondary.fields()) {
        sec.put(f.name(), f.value());
      }
    }
    Map<String, CrossCheck> out = new HashMap<>();
    if (primary == null || primary.fields() == null) {
      return out;
    }
    for (RecognizedField f : primary.fields()) {
      String other = sec.get(f.name());
      if (other == null) {
        out.put(f.name(), CrossCheck.NOT_RUN);
      } else if (agree(f.value(), other)) {
        out.put(f.name(), CrossCheck.MATCH);
      } else {
        out.put(f.name(), CrossCheck.MISMATCH);
      }
    }
    return out;
  }

  private static boolean agree(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    String na = a.trim();
    String nb = b.trim();
    Double da = asNumber(na);
    Double db = asNumber(nb);
    if (da != null && db != null) {
      return Math.abs(da - db) <= TOLERANCE;
    }
    return na.equalsIgnoreCase(nb);
  }

  private static Double asNumber(String s) {
    String cleaned = s.replaceAll("[\\s¥￥$,]", "");
    if (cleaned.matches("\\d+(\\.\\d{1,2})?")) {
      return Double.parseDouble(cleaned);
    }
    return null;
  }
}
