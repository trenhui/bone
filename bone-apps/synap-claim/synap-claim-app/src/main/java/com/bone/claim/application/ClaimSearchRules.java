package com.bone.claim.application;

// 跨键检索的护栏：显式拒绝而不是静默裁剪，LIKE 通配符必须转义。
public final class ClaimSearchRules {

  public static final int MIN_KEYWORD_LENGTH = 2;
  public static final int MAX_KEYWORD_LENGTH = 64;
  public static final int MAX_LIMIT = 20;
  private static final String FORBIDDEN = "'\"\\;<>";

  private ClaimSearchRules() {}

  public static String validate(String rawKeyword, int limit) {
    String k = rawKeyword == null ? "" : rawKeyword.trim();
    if (k.length() < MIN_KEYWORD_LENGTH || k.length() > MAX_KEYWORD_LENGTH) {
      return "关键词长度需 " + MIN_KEYWORD_LENGTH + ".." + MAX_KEYWORD_LENGTH + " 字符；过短会退化为全表扫描，过长不做静默截断";
    }
    for (int i = 0; i < k.length(); i++) {
      char ch = k.charAt(i);
      if (Character.isISOControl(ch) || FORBIDDEN.indexOf(ch) >= 0) {
        return "关键词含不允许的字符（控制字符 / 引号 / 反斜杠 / 分号 / 尖括号）：" + "检索键只允许业务号码、遮蔽值与中文姓名";
      }
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      return "limit 需 1.." + MAX_LIMIT + "：上限是防全库拉取的护栏，超限显式拒绝";
    }
    return null;
  }

  // ESCAPE '!' 与模板配套：% / _ / ! 全部转成字面量，用户无法用通配符放大扫描面。
  public static String toLikePattern(String rawKeyword) {
    String k = rawKeyword == null ? "" : rawKeyword.trim();
    StringBuilder sb = new StringBuilder(k.length() + 8);
    for (int i = 0; i < k.length(); i++) {
      char ch = k.charAt(i);
      if (ch == '!' || ch == '%' || ch == '_') {
        sb.append('!');
      }
      sb.append(ch);
    }
    return "%" + sb + "%";
  }
}
