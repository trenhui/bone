package com.bone.claim.domain.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 送模型前 PII 遮蔽管道（AI 安全三件套之一，§11.3 / AI 技术方案 §6.4）。
 *
 * <p>上传材料是用户可控的不可信输入，直接送 LLM 会引入 PII 泄漏路径——本管道把 身份证号/手机号/银行卡号/就诊卡号替换为内存占位符，<b>映射表仅存当次请求、不落盘、不入
 * trace</b> （§11.3 S 级数据不出域）。姓名走边界收紧的中文词规则（避免误伤正文，NER 双路为二期增强）。
 *
 * <p>设计约束：本类是纯函数式领域不变量，不依赖任何框架；遮蔽/还原在同一请求生命周期内配对使用。
 */
public final class PiiMasker {

  /** 遮蔽后占位符模板（token 内不含原始值）。 */
  private static final class Rule {
    final String type;
    final Pattern pattern;
    final int keepPrefix;
    final int keepSuffix;

    Rule(String type, String regex, int keepPrefix, int keepSuffix) {
      this.type = type;
      this.pattern = Pattern.compile(regex);
      this.keepPrefix = keepPrefix;
      this.keepSuffix = keepSuffix;
    }
  }

  private static final Rule[] RULES = {
    // 身份证号：17 位数字 + 校验位（数字或 X），保留前 6 后 4
    new Rule("ID_CARD", "\\b\\d{17}[\\dXx]\\b", 6, 4),
    // 手机号：1[3-9] 开头 11 位，保留前 3 后 4
    new Rule("PHONE", "(?<![\\d])1[3-9]\\d{9}(?![\\d])", 3, 4),
    // 银行卡号：16–19 位，保留前 6 后 4
    new Rule("BANK_CARD", "(?<![\\d])\\d{16,19}(?![\\d])", 6, 4),
    // 就诊卡号：字母数字 8–20 位（边界收紧）
    new Rule("MED_CARD", "(?<![\\w])[A-Za-z0-9]{8,20}(?![\\w])", 4, 4),
    // 姓名：标点/空白边界内的 2–3 个汉字（降低正文误伤）
    new Rule("NAME", "(?<=[\\s，。、：:（(（])[\\u4e00-\\u9fa5]{2,3}(?=[\\s，。、：:）)）])", 1, 0),
  };

  private PiiMasker() {}

  /** 遮蔽：返回占位文本 + 可逆映射（调用方持有，禁持久化）。 */
  public static MaskedText mask(String text) {
    if (text == null) {
      return new MaskedText("", Map.of());
    }
    String result = text;
    Map<String, String> tokenMap = new LinkedHashMap<>();
    int idx = 0;
    for (Rule rule : RULES) {
      Matcher matcher = rule.pattern.matcher(result);
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
        String original = matcher.group();
        String token = "<PII:" + rule.type + "#" + (idx++) + ">";
        tokenMap.put(token, original);
        matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
      }
      matcher.appendTail(sb);
      result = sb.toString();
    }
    return new MaskedText(result, Map.copyOf(tokenMap));
  }
}
