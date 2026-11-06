package com.bone.aidoc.domain.service;

/**
 * 中文大写金额解析（理赔票据「金额大小写一致性」L2 规则的子工具，纯函数、无副作用）。
 *
 * <p>支持：零壹贰叁肆伍陆柒捌玖 + 拾佰仟万 + 亿 + 圆/元/角/分/整。 解析失败时返回 {@code NaN}——
 * 调用方据此将该字段判为「不适用规则」而非「校验失败」，避免对无法判定的金额误报。
 *
 * <p>典型输入：「壹万贰仟伍佰捌拾元整」「壹万贰仟伍佰捌拾元伍角叁分」「壹佰贰拾叁元肆角伍分」。
 */
final class ChineseAmount {

  private static final java.util.Map<Character, Long> DIGITS = new java.util.HashMap<>();
  private static final java.util.Map<Character, Long> UNITS = new java.util.HashMap<>();

  static {
    DIGITS.put('零', 0L);
    DIGITS.put('壹', 1L);
    DIGITS.put('贰', 2L);
    DIGITS.put('叁', 3L);
    DIGITS.put('肆', 4L);
    DIGITS.put('伍', 5L);
    DIGITS.put('陆', 6L);
    DIGITS.put('柒', 7L);
    DIGITS.put('捌', 8L);
    DIGITS.put('玖', 9L);
    UNITS.put('拾', 10L);
    UNITS.put('佰', 100L);
    UNITS.put('仟', 1000L);
    UNITS.put('万', 10_000L);
    UNITS.put('亿', 100_000_000L);
  }

  private static final String ALLOWED = "零壹贰叁肆伍陆柒捌玖拾佰仟万亿圆元角分整點点";

  private ChineseAmount() {}

  /** 从一段原始文字中抽取中文大写金额串（首个中文数字起，到金额字符结束）。无法抽取返回 null。 */
  static String extract(String text) {
    if (text == null || text.isEmpty()) {
      return null;
    }
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      if (DIGITS.containsKey(text.charAt(i))) {
        start = i;
        break;
      }
    }
    if (start < 0) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (ALLOWED.indexOf(c) >= 0) {
        sb.append(c);
      } else {
        break;
      }
    }
    String s = sb.toString();
    // 必须含货币单位（元/圆/角/分），否则无法判定量级
    if (s.indexOf('元') < 0 && s.indexOf('圆') < 0 && s.indexOf('角') < 0 && s.indexOf('分') < 0) {
      return null;
    }
    return s;
  }

  /** 解析中文大写金额为元（double，含两位以内小数）。失败返回 NaN。 */
  static double parseYuan(String cn) {
    if (cn == null || cn.isEmpty()) {
      return Double.NaN;
    }
    String yuan = cn;
    // 拆分整数段（元前）与小数段（元后）
    int idx = yuan.indexOf('元');
    if (idx < 0) {
      idx = yuan.indexOf('圆');
    }
    String integerPart;
    String decimalPart;
    if (idx >= 0) {
      integerPart = yuan.substring(0, idx);
      decimalPart = yuan.substring(idx + 1);
    } else {
      integerPart = yuan;
      decimalPart = "";
    }
    long intVal = parseInteger(integerPart);
    if (intVal < 0) {
      return Double.NaN;
    }
    double decimal = parseDecimal(decimalPart);
    if (Double.isNaN(decimal)) {
      return Double.NaN;
    }
    return intVal + decimal;
  }

  private static long parseInteger(String s) {
    if (s == null || s.isEmpty()) {
      return 0;
    }
    long total = 0;
    long section = 0;
    long num = 0;
    boolean anyDigit = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (DIGITS.containsKey(c)) {
        num = DIGITS.get(c);
        anyDigit = true;
      } else if (UNITS.containsKey(c)) {
        long u = UNITS.get(c);
        if (u == 10_000 || u == 100_000_000) {
          section = (section + num) * u;
          total += section;
          section = 0;
        } else {
          section += (num == 0 ? 1 : num) * u;
        }
        num = 0;
      } else {
        return -1;
      }
    }
    total += section + num;
    return anyDigit ? total : -1;
  }

  private static double parseDecimal(String s) {
    if (s == null || s.isEmpty()) {
      return 0.0;
    }
    double dec = 0.0;
    int jiao = s.indexOf('角');
    int fen = s.indexOf('分');
    if (jiao >= 0) {
      char d = s.charAt(jiao - 1);
      if (!DIGITS.containsKey(d)) {
        return Double.NaN;
      }
      dec += DIGITS.get(d) * 0.1;
    }
    if (fen >= 0) {
      char d = s.charAt(fen - 1);
      if (!DIGITS.containsKey(d)) {
        return Double.NaN;
      }
      dec += DIGITS.get(d) * 0.01;
    }
    return dec;
  }
}
