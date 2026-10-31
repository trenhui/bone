package com.bone.claim.domain.model.adjustment.engine;

/**
 * 规则缺口（fail-closed 信号）：<b>配置缺失或能力未定义</b>时，引擎拒绝出一个"看起来合理"的数字。
 *
 * <p><b>为什么不是抛通用异常、也不是返回 0</b>：理算错在"猜"上，而不在"拒"上。 一个没配赔付比例的责任，若引擎回落到"默认 90%"，系统会安静地按错的金额赔一整年； 若回落到
 * 0，客户会被无理由拒赔。两者的共同点是<b>没有人知道是配置问题</b>。 故此类携带 {@link Code} + 定位信息一路抛到应用层，由编排决定"转人工"还是"退回补配置"。
 *
 * <p><b>与 {@code Accumulation.available=false} 的分工</b>：那是"数据读不到但仍可给人参考"（只标记不中断）；
 * 这是"规则本身不成立，算出来的数没有意义"（必须中断）。混为一谈会让缺配置的案子带着错金额流到付款环节。
 */
public final class RuleGap extends RuntimeException {

  /** 缺口分类（受控枚举，禁自由文本——原因码要能进报表、能按类分派）。 */
  public enum Code {
    /** 责任形式不是费用补偿型（定额给付 / 津贴给付在 V1 无规则）。 */
    LIABILITY_FORM_UNSUPPORTED,
    /** 赔付比例多维表未命中（V1 §4.2 步 3：匹配不中必须 fail-closed，不得回落默认值）。 */
    RATIO_NOT_MATCHED,
    /** 赔付比例命中多格（区间重叠 / 维度未收敛）。 */
    RATIO_AMBIGUOUS,
    /** 免赔配置自相矛盾（如相对免赔 + 免赔比例、按天数免赔用于费用补偿型）。 */
    DEDUCTIBLE_CONFIG_INVALID,
    /** 控额层要求额度但额度未定义（外部控额 / 共保池来源缺失）。 */
    LIMIT_LAYER_UNDEFINED,
    /** 共享保额：池额度来源、耗尽回落、多池截断顺序均未定义（Q-02）。 */
    SHARED_LIMIT_UNSUPPORTED,
    /** 先赔后赔：三种关系的金额推导公式全部缺失（Q-01）。 */
    PRIORITY_CHAIN_UNSUPPORTED,
    /** 外部控额（直付 / 保司）：超时、幂等、对账口径未定义（Q-07）。 */
    EXTERNAL_QUOTA_UNSUPPORTED,
    /** 日赔付限额：日窗口判不出来（明细缺发票日期 / 未提供赔案受理日）。 */
    DAILY_LIMIT_WINDOW_UNDETERMINED,
    /** 日赔付限额：账本给出的窗口晚于本案业务日（日口径串了），不能拿别天的用量算这天。 */
    DAILY_LIMIT_WINDOW_STALE,
    /** 等待期内出险，不进理算（V1 §4.1 前置校验）。 */
    WAITING_PERIOD_NOT_MET,
    /** 适用限定（区域/医院/药品/病种白黑名单）不符，前置校验即退。 */
    LIMITED_CONDITION_NOT_MET,
    /** 票面结算数据与费用池不自洽（自付一定义冲突 / 三式差额异常，Q-08）。 */
    INVOICE_SETTLEMENT_INCONSISTENT,
    /** 「次」的判定维度在明细里不存在（配了同日/同院分组却无发票日期等）：并成一组会少赔。 */
    OCCURRENCE_DIMENSION_MISSING,
    /** 「次」的判定维度只存在于部分明细上（同案内不一致）：按存在与否分组会多赔一次次限额。 */
    OCCURRENCE_DIMENSION_INCONSISTENT,
    /** 入参结构非法（无明细 / 金额为负等）。 */
    INVALID_INPUT
  }

  private final Code code;
  private final String detail;

  private RuleGap(Code code, String detail, Throwable cause) {
    super("[" + code.name() + "] " + detail, cause);
    this.code = code;
    this.detail = detail;
  }

  public static RuleGap of(Code code, String detail) {
    return new RuleGap(code, detail, null);
  }

  public static RuleGap of(Code code, String format, Object... args) {
    return new RuleGap(code, format.formatted(args), null);
  }

  public static RuleGap wrap(Code code, String detail, Throwable cause) {
    return new RuleGap(code, detail, cause);
  }

  public Code code() {
    return code;
  }

  public String detail() {
    return detail;
  }
}
