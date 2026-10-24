package com.bone.airag.domain.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 写入侧 PII 字段级掩码（2026-10-24 企业复杂场景诊断 P0-3 落地）：ingest 时对高敏模式做<b>等长局部掩码</b>。
 *
 * <p><b>为什么在写入侧而不只在出口</b>：出口脱敏（网关）只保护「出站请求 / 证据落库」，{@code ai_rag_chunk.chunk_text} 仍以
 * <b>明文</b>落库——DB / 备份 / 运维通道一旦泄露即明文外泄，与「数据不出域 / 最小暴露」原则不自洽。入库侧掩码后形成 「入库 → 存储 → 检索 → 生成出口」全链路 PII
 * 闭环。
 *
 * <p><b>两个设计约束</b>：
 *
 * <ul>
 *   <li><b>等长替换</b>：掩码字符数与被掩码段一致，{@code spanStart/spanEnd} 锚点不漂移（引用定位原文的契约不变）；
 *   <li><b>保留检索语义的局部掩码</b>：身份证保留前 6 后 4（地址段 + 顺序码可检索）、手机号保留前 3 后 4（号段 + 尾号）、 银行卡保留后 4、邮箱保留首字符 +
 *       域名——精确串查询（如完整身份证号）不再命中，但分段 / 号段检索仍可用， 这是「最小暴露」与「检索可用」之间的工程折中（需彻底不可检索时应改为令牌化 + 映射表，属目标态）。
 * </ul>
 *
 * <p><b>纯函数、零外部依赖</b>：判定规则全部在本类（ArchUnit 禁应用服务承载领域规则），应用层只做编排与事件下沉。 模式按固定顺序应用（身份证 → 银行卡 → 手机号 →
 * 邮箱），掩码后的星号段不会再被后续模式命中（不递归放大掩码范围）。
 */
public final class SensitiveDataMasker {

  /** 身份证（18 位，末位可 X）：保留前 6 后 4。 */
  private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");

  /** 银行卡（16–19 位，过 Luhn 校验防误伤）：保留后 4。 */
  private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");

  /** 手机号（11 位，1[3-9] 开头）：保留前 3 后 4。 */
  private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

  /** 邮箱：本地部分保留首字符（等长），域名保留（域名非 PII，且是路由必需）。 */
  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private static final char MASK = '*';

  private final boolean enabled;

  private SensitiveDataMasker(boolean enabled) {
    this.enabled = enabled;
  }

  /** 启用实例（默认形态，{@code bone.ai.rag.ingest.pii-mask-enabled=true}）。 */
  public SensitiveDataMasker() {
    this(true);
  }

  /** 禁用实例（Null Object）：{@link #mask(String)} 原样返回——调用方无需分支判断开关。 */
  public static SensitiveDataMasker disabled() {
    return new SensitiveDataMasker(false);
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 掩码一段文本（等长）。
   *
   * @return 掩码后文本（长度与入参一致）；未启用时原样返回
   */
  public String mask(String text) {
    if (!enabled || text == null || text.isEmpty()) {
      return text;
    }
    String masked = maskPattern(text, ID_CARD, 6, 4);
    masked = maskBankCards(masked);
    masked = maskPattern(masked, PHONE, 3, 4);
    masked = maskEmails(masked);
    return masked;
  }

  /**
   * 统计文本中的高敏命中数（用于 ingest 事件明细：掩码了多少处，可观测而非静默）。
   *
   * <p>与 {@link #mask(String)} 同序同模式——先掩码再计数会得到 0，故独立走一遍匹配（写入路径非热路径， 成本是几次正则 扫描，可接受）。
   */
  public int countHits(String text) {
    if (!enabled || text == null || text.isEmpty()) {
      return 0;
    }
    int n = count(ID_CARD, text) + countBankCards(text) + count(PHONE, text) + count(EMAIL, text);
    return n;
  }

  // ---- internals ----

  /** 通用模式：保留前 {@code keepPrefix} + 后 {@code keepSuffix}，中间等长掩码。 */
  private static String maskPattern(String text, Pattern pattern, int keepPrefix, int keepSuffix) {
    Matcher m = pattern.matcher(text);
    StringBuilder sb = new StringBuilder(text.length());
    int last = 0;
    while (m.find()) {
      String hit = m.group();
      int maskFrom = Math.min(keepPrefix, hit.length());
      int maskTo = Math.max(maskFrom, hit.length() - keepSuffix);
      sb.append(text, last, m.start()).append(hit, 0, maskFrom);
      for (int i = maskFrom; i < maskTo; i++) {
        sb.append(MASK);
      }
      sb.append(hit, maskTo, hit.length());
      last = m.end();
    }
    sb.append(text, last, text.length());
    return sb.toString();
  }

  /** 银行卡：仅对通过 Luhn 校验的候选掩码（16–19 位数字串也可能是订单号 / 编号，防误伤）。 */
  private static String maskBankCards(String text) {
    Matcher m = BANK_CARD.matcher(text);
    StringBuilder sb = new StringBuilder(text.length());
    int last = 0;
    while (m.find()) {
      String hit = m.group();
      sb.append(text, last, m.start());
      if (passesLuhn(hit)) {
        sb.append(maskTail(hit, 4));
      } else {
        sb.append(hit);
      }
      last = m.end();
    }
    sb.append(text, last, text.length());
    return sb.toString();
  }

  /** 邮箱：本地部分除首字符外等长掩码，域名原样（域名是路由必需信息，非 PII）。 */
  private static String maskEmails(String text) {
    Matcher m = EMAIL.matcher(text);
    StringBuilder sb = new StringBuilder(text.length());
    int last = 0;
    while (m.find()) {
      String hit = m.group();
      int at = hit.indexOf('@');
      sb.append(text, last, m.start());
      sb.append(hit.charAt(0));
      for (int i = 1; i < at; i++) {
        sb.append(MASK);
      }
      sb.append(hit, at, hit.length());
      last = m.end();
    }
    sb.append(text, last, text.length());
    return sb.toString();
  }

  private static String maskTail(String hit, int keepSuffix) {
    int maskTo = hit.length() - keepSuffix;
    StringBuilder sb = new StringBuilder(hit.length());
    for (int i = 0; i < maskTo; i++) {
      sb.append(MASK);
    }
    sb.append(hit, maskTo, hit.length());
    return sb.toString();
  }

  private static int count(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    int n = 0;
    while (m.find()) {
      n++;
    }
    return n;
  }

  private static int countBankCards(String text) {
    Matcher m = BANK_CARD.matcher(text);
    int n = 0;
    while (m.find()) {
      if (passesLuhn(m.group())) {
        n++;
      }
    }
    return n;
  }

  /** Luhn（模 10）校验：银行卡号校验位算法，与网关出口脱敏的误伤闸门同口径。 */
  static boolean passesLuhn(String digits) {
    int sum = 0;
    boolean alternate = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int n = digits.charAt(i) - '0';
      if (alternate) {
        n *= 2;
        if (n > 9) {
          n -= 9;
        }
      }
      sum += n;
      alternate = !alternate;
    }
    return sum % 10 == 0;
  }
}
