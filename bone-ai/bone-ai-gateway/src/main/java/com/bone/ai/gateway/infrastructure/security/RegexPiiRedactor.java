package com.bone.ai.gateway.infrastructure.security;

import com.bone.ai.gateway.domain.gateway.PiiRedactor;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * 正则规则脱敏器（零外部依赖的默认实现）。
 *
 * <p><b>覆盖范围（按国内企业合规高频项排序）</b>：
 *
 * <ol>
 *   <li>中国居民身份证 18 位（含 ISO 7064 MOD 11-2 校验位，降低误伤）；
 *   <li>中国大陆手机号（{@code 1[3-9]\d{9}}，带前瞻避免截取长数字串）；
 *   <li>银行卡号 13-19 位（Luhn 校验，减少订单号误判）；
 *   <li>邮箱地址；
 *   <li>IPv4 地址（内网段常见于日志回显）。
 * </ol>
 *
 * <p><b>误伤控制</b>：纯数字串容易与订单号 / 金额混淆，故手机号、银行卡、身份证均要求<b>校验位或长度边界</b>通过才脱敏；
 * 这是「宁可漏一个也不误伤业务字段」的取舍——误伤会让模型判定依据消失，漏一个则是合规风险。
 *
 * <p>掩码策略：保留首尾特征位，中间 {@code *}（前 1 后 1 起步，长值适当多保留几位），兼顾可核对性与最小暴露。
 */
public class RegexPiiRedactor implements PiiRedactor {

  private final boolean enabled;
  private final List<Rule> rules;

  public RegexPiiRedactor(boolean enabled) {
    this.enabled = enabled;
    this.rules = enabled ? defaultRules() : List.of();
  }

  /** 已启用但无额外规则的实例（默认装配用）。 */
  public RegexPiiRedactor() {
    this(true);
  }

  @Override
  public boolean enabled() {
    return enabled;
  }

  @Override
  public String redact(String text) {
    if (!enabled || text == null || text.isEmpty()) {
      return text;
    }
    String out = text;
    for (Rule rule : rules) {
      out =
          rule.pattern()
              .matcher(out)
              .replaceAll(
                  match ->
                      rule.guard().test(match.group())
                          ? rule.mask() + rule.unmasker().apply(match.group())
                          : match.group());
    }
    return out;
  }

  private static List<Rule> defaultRules() {
    Predicate<String> any = t -> true;
    return List.of(
        new Rule(
            Pattern.compile("(?<![0-9A-Za-z])\\d{17}[0-9Xx](?![0-9A-Za-z])"),
            "[身份证]",
            RegexPiiRedactor::hasValidIdChecksum,
            RegexPiiRedactor::maskIdCard),
        new Rule(
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            "[手机号]",
            any,
            RegexPiiRedactor::maskPhone),
        new Rule(
            Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)"),
            "[卡号]",
            RegexPiiRedactor::luhnValid,
            RegexPiiRedactor::maskCard),
        new Rule(
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "[邮箱]",
            any,
            m -> String.valueOf(m.charAt(0)) + "***"),
        new Rule(
            Pattern.compile("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])"),
            "[IP]",
            any,
            RegexPiiRedactor::maskIp));
  }

  // ---- 校验位（误伤控制的核心）----
  //
  // 纯长度匹配的脱敏器会把订单号、金额、批次号全部打成 PII——模型随之失去判定依据，比漏脱一个更糟。
  // 因此身份证与银行卡要求校验位通过才脱敏：宁可漏一个格式非法的真 PII，也不误伤业务字段。

  /** 身份证 ISO 7064 MOD 11-2 校验位（18 位，末位校验码，10 → 'X'）。 */
  private static boolean hasValidIdChecksum(String id) {
    if (id.length() != 18) {
      return false;
    }
    int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    int sum = 0;
    for (int i = 0; i < 17; i++) {
      char c = id.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
      sum += (c - '0') * weights[i];
    }
    int expected = (12 - sum % 11) % 11;
    char check = Character.toUpperCase(id.charAt(17));
    return check == (expected == 10 ? 'X' : (char) ('0' + expected));
  }

  /** 银行卡 Luhn 校验（ISO/IEC 7812）。 */
  private static boolean luhnValid(String number) {
    int sum = 0;
    boolean dbl = false;
    for (int i = number.length() - 1; i >= 0; i--) {
      char c = number.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
      int d = c - '0';
      if (dbl) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      dbl = !dbl;
    }
    return sum % 10 == 0;
  }

  // ---- 掩码策略（返回「打码后仍可见的部分」，类型标签前缀由 Rule.mask 提供）----

  /** 身份证：保留前 6 位（行政区划）与后 4 位（校验位便于人工核对），中间打码。 */
  private static String maskIdCard(String id) {
    return id.substring(0, 6) + "********" + id.substring(14);
  }

  /** 手机号：保留前 3 后 4。 */
  private static String maskPhone(String phone) {
    return phone.substring(0, 3) + "****" + phone.substring(7);
  }

  private static String maskCard(String card) {
    return card.substring(0, 4) + "********" + card.substring(card.length() - 4);
  }

  /** IPv4：保留首段，打码后三段。 */
  private static String maskIp(String ip) {
    return ip.substring(0, ip.indexOf('.')) + ".***.***.***";
  }

  /** 单条规则：命中模式 + 类型标签 + 校验位闸门（不通过则不脱敏，防误伤业务字段）+ 掩码策略。 */
  private record Rule(
      Pattern pattern, String mask, Predicate<String> guard, UnaryOperator<String> unmasker) {}
}
