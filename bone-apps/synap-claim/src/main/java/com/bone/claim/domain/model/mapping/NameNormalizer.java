package com.bone.claim.domain.model.mapping;

import java.util.Locale;

/**
 * 名称归一化：只服务<b>候选匹配</b>，不服务判定。
 *
 * <p>规则刻意保守——去空白、全角转半角、统一括号、去掉常见后缀分隔符。**不做**同义词替换、 不做拼音、不做模糊纠错：这些会制造"看起来很像"的假阳性，而映射错了会直接赔错钱。
 */
public final class NameNormalizer {

  private NameNormalizer() {}

  public static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(raw.length());
    for (char ch : raw.toCharArray()) {
      char c = ch;
      if (c == '\u3000') {
        continue;
      }
      if (Character.isWhitespace(c)) {
        continue;
      }
      // 全角 ASCII（！-～）转半角
      if (c >= '\uFF01' && c <= '\uFF5E') {
        c = (char) (c - 0xFEE0);
      }
      // 括号统一到半角：全角/方头括号混用是中文主数据最常见的"看起来一样、比不出来"
      switch (c) {
        case '（' -> c = '(';
        case '）' -> c = ')';
        case '【' -> c = '[';
        case '】' -> c = ']';
        default -> {
          // keep
        }
      }
      sb.append(c);
    }
    return sb.toString().toLowerCase(Locale.ROOT);
  }
}
