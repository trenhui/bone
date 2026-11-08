package com.bone.claim.domain.model.evidence;

import java.util.List;
import java.util.Locale;

/**
 * 材料事实出域策略：决定「感知层解析出的哪些字段可以送去模型」。
 *
 * <p><b>为什么需要一个显式策略</b>：认知层此前根本拿不到材料（只收到 6 个聚合标量），补齐这条链时若图省事把 {@code extracted_json}
 * 整个塞进提示词，就等于把材料里的一切——包括姓名、证件号、病历号——一起出域到第三方模型。这里给出的是这道边界的**唯一真源**，两个消费方（案件上下文适配器、后续任何新增的认知层入口）共用它，避免各写一份
 * denylist 后逐渐漂移。
 *
 * <p><b>为什么是 denylist 而不是 allowlist</b>：字段码由感知层产出，新增材料类型会带来新码（{@code invoice_no} / {@code
 * diagnosis_code} / 将来的 {@code discharge_summary}）。allowlist 的失败形态是「新材料一律送不进去，AI 看到空材料」——
 * 而这类静默失效在生产上极难发现（模型照样返回一份看起来合理的结论）。denylist 的失败形态是「新出现的个人标识字段被送出去」，由下面第二道闸门兜住。
 *
 * <p><b>它与网关脱敏的分工（两层，互不替代）</b>：权威控制点在 LLM 网关出口（{@code
 * LlmGatewayApplicationService#redactRequest}，{@code bone.ai.gateway.pii-redaction-enabled} 默认开启），
 * 那里对身份证 / 手机号 / 银行卡 / 邮箱 / IP 做掩码。本策略是应用层的第二层：拦掉网关**规则覆盖不到**的字段（首当其冲是姓名——中文姓名无法用正则可靠识别），
 * 以及「即便脱敏也不该出域」的定位性标识（病历号、病历 ID 能反查到人）。两层都失效才会明文出域，这是纵深防御而非冗余。
 */
public final class EvidenceFactPolicy {

  /** 单次送进提示词的事实条数上限：材料多的案件不能把提示词撑爆（成本与截断风险都按条数计）。 */
  public static final int MAX_FACTS = 40;

  /** 单值长度上限：OCR 偶发把整段文本塞进一个字段，超长值必须截断而不是整条丢弃。 */
  public static final int MAX_VALUE_LENGTH = 120;

  /**
   * 禁止出域的字段码（已归一化：仅小写字母与数字）。
   *
   * <p><b>为什么不按 {@code name} 片段匹配</b>：{@code item_name}（药品名）、{@code hospital_name} 含 {@code name}
   * 但恰恰是临床核验必需的信息，片段匹配会把它们一起拦掉——那正是「配了不生效」的典型形态。故此处按**归一整码**精确匹配， 新码默认放行。
   */
  private static final List<String> DENIED_CODES =
      List.of(
          // 姓名类（网关正则覆盖不到，是这一层存在的主要理由）
          "name",
          "patientname",
          "insuredname",
          "personname",
          "customername",
          "applyname",
          // 证件 / 主体标识
          "idcard",
          "idcardno",
          "idno",
          "identityno",
          "identitycard",
          "patientid",
          "insuredid",
          "passportno",
          "ssn",
          // 联系方式
          "phone",
          "mobile",
          "telephone",
          "tel",
          "contactphone",
          "email",
          "mailbox",
          "wechat",
          "wechatno",
          // 账户 / 地址
          "bankaccount",
          "bankcard",
          "bankcardno",
          "accountno",
          "address",
          "homeaddress",
          // 可反查到人的业务标识
          "medicalrecordno",
          "medicalrecordid",
          "birthday",
          "birthdate");

  private EvidenceFactPolicy() {}

  /** 字段码是否允许出域；空码一律拒绝（无从判断就不出域）。 */
  public static boolean exportable(String code) {
    String normalized = normalizeCode(code);
    return !normalized.isEmpty() && !DENIED_CODES.contains(normalized);
  }

  /** 值归一：去首尾空白 + 超长截断。空值由调用方跳过（空事实没有核验与展示价值）。 */
  public static String normalizeValue(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.length() <= MAX_VALUE_LENGTH ? trimmed : trimmed.substring(0, MAX_VALUE_LENGTH);
  }

  /**
   * 字段码归一：去掉分隔符并转小写，使 {@code idCardNo} / {@code id_card_no} / {@code ID_CARD_NO} 等价。
   *
   * <p>公开是因为「模型输出的事实码」与「材料侧的字段码」要做同一套归一才能相等比较——两边用两套归一 就会出现「明明是同一条事实却匹配不上」的静默丢弃。
   */
  public static String normalizeCode(String code) {
    if (code == null) {
      return "";
    }
    StringBuilder normalized = new StringBuilder(code.length());
    for (int i = 0; i < code.length(); i++) {
      char c = code.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        normalized.append(Character.toLowerCase(c));
      }
    }
    return normalized.toString().toLowerCase(Locale.ROOT);
  }
}
