package com.bone.claim.domain.model.adjustment.engine;

import java.time.LocalDate;
import java.util.Map;

/**
 * 目录准入结论（缺口 ① 费用明细筛选 / 适用限定「药品·诊疗」维度的共同载体，V1 §3.5）。
 *
 * <p><b>为什么目录结论要作为"事实"从外部传进来，而不是引擎自己去查</b>：理算聚合必须是无副作用的纯计算
 * （"同输入同输出"是理算可复算的合规硬要求）。目录是<b>外部主数据</b>——它有版本、有生效期、可能由第三方系统提供，
 * 本仓库不持有一份副本，否则立刻出现"两个真源、谁新谁旧说不清"。故：应用层按业务日去查，把结论物化进快照，引擎只消费。
 *
 * <p><b>为什么是三态而不是布尔</b>：{@code 在目录里 / 不在目录里} 只覆盖了"能查"的情形。真实世界里还有两种 "查不了"：
 *
 * <ul>
 *   <li><b>明细没有编码</b>（{@code item_code} 空）——录入漏了，不是目录外；
 *   <li><b>目录本身取不到</b>（未接入 / 版本缺失 / 该业务日无生效版本）——系统问题，不是客户问题。
 * </ul>
 *
 * <p>把这两种并进"不在目录里"就是<b>替客户下拒赔结论</b>（原因只是数据没到），并进"在目录里"则是<b>白名单白配</b> （照赔不误）。两者都是猜，故单独立 {@link
 * Verdict#UNKNOWN}：引擎见到它就 {@code CATALOG_UNRESOLVED} 退补件，交人工判定，不产生金额。
 *
 * <p><b>与 {@code social_flag} 的分工</b>：{@code social_flag} 是医保口径（医保目录内/外），决定落哪个费用池；
 * 本类是<b>这份责任自己的</b>保障范围（可能比医保目录更窄，如"只保甲类"），决定这条明细是否进入可赔基数。 两者不可互相顶替：医保目录外的自费药，某些责任仍然赔（进 {@code
 * TOTAL_SELF_PAY} 池）。
 *
 * @param mode 准入策略：{@code NONE} 不查目录（历史行为，逐字不变）/{@code MANDATORY} 必须查到结论
 * @param catalogCode 目录编码（如 {@code NATIONAL_2024}）：由责任配置指定，回答"按哪一版目录判"
 * @param asOf 判定基准日（发票日期，缺失回落赔案受理日）：目录有生效期，用今天的目录算去年的票必然错
 * @param verdicts 每条明细的准入结论（键为 {@code itemId}）
 */
public record CatalogAdmission(
    Mode mode, String catalogCode, LocalDate asOf, Map<Long, Verdict> verdicts) {

  /** 准入策略（受控枚举，禁自由文本）。 */
  public enum Mode {
    /** 不查目录：责任没配，行为与引入本机制之前逐字一致（F1 双轨回归绿的前提）。 */
    NONE,
    /** 必须查到每一条明细的结论：查不到即 fail-closed，不允许"当作在目录里"。 */
    MANDATORY;

    /** 解析编码。无法识别必须抛错而不是回落 {@code NONE}：回落等于"写错一个字母，目录准入静默失效"， 而这正是本机制要消灭的那类缺口。 */
    public static Mode fromCode(String code) {
      if (code == null || code.isBlank()) {
        return NONE;
      }
      String trimmed = code.trim();
      for (Mode mode : values()) {
        if (mode.name().equalsIgnoreCase(trimmed)) {
          return mode;
        }
      }
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "无法识别的目录准入策略「%s」：只允许 NONE（不查目录）/ MANDATORY（必须查到结论）。" + "按未知值当作 NONE 会让目录准入静默失效",
          code);
    }
  }

  /** 单条明细的准入结论。 */
  public enum Verdict {
    /** 目录内且本责任保障：正常进入可赔基数。 */
    COVERED,
    /** 目录外 / 该责任不保障：从可赔基数剔除，金额计入剔除诊断与快照。 */
    EXCLUDED,
    /** 判不出来（缺编码 / 目录取不到 / 该业务日无生效版本）：fail-closed，不产生金额。 */
    UNKNOWN
  }

  /** 未启用目录准入（唯一合法的不启用形态）。 */
  public static final CatalogAdmission INACTIVE =
      new CatalogAdmission(Mode.NONE, "", null, Map.of());

  public CatalogAdmission {
    mode = mode == null ? Mode.NONE : mode;
    catalogCode = catalogCode == null ? "" : catalogCode.trim();
    // 校验写在规范构造器里，而不是只写在 of(...) 里：任何人直接 new 一份也绕不过去。
    // 否则"配了目录编码但策略 NONE"这种半配置会安静地失效——而那恰好是本机制要消灭的形态。
    if (mode == Mode.NONE && !catalogCode.isEmpty()) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "配置了目录编码「%s」但准入策略为 NONE：该编码永远不会被查询（配了不生效 ⇒ 超赔），" + "要么把策略改为 MANDATORY，要么清空目录编码",
          catalogCode);
    }
    // NONE ⇒ 结论一律清空。留着会给后来者一条隐式路径："结论都传进来了，应该生效了吧"——
    // 而实际上没有任何代码会读它。要么显式开 MANDATORY，要么当它不存在。
    verdicts = mode == Mode.NONE || verdicts == null ? Map.of() : Map.copyOf(verdicts);
  }

  /** 是否真的在查目录（{@code NONE} 时引擎走与历史完全一致的路径）。 */
  public boolean active() {
    return mode == Mode.MANDATORY;
  }

  /** 取某明细的结论：缺条目一律 {@link Verdict#UNKNOWN}（"没结论" ≠ "在目录里"）。 */
  public Verdict verdictOf(Long itemId) {
    if (itemId == null) {
      return Verdict.UNKNOWN;
    }
    Verdict verdict = verdicts.get(itemId);
    return verdict == null ? Verdict.UNKNOWN : verdict;
  }

  /**
   * 按责任配置构造准入结论，并做<b>反向一致性校验</b>（与日限额 Q-05 同一套纪律）。
   *
   * <p>被拦住的都是"配了不生效"的形态：有编码没开策略（配了以为生效）、开了策略没编码（不知道该查哪一版）、
   * 开了策略没基准日（不知道该用哪天的版本）、开了策略却一条结论都没有（端口没接好）。
   *
   * @param policy 责任上的准入策略编码（{@code NONE} / {@code MANDATORY}）
   * @param catalogCode 责任上的目录编码
   * @param asOf 判定基准日；{@code NONE} 时忽略
   * @param verdicts 端口查得的结论；{@code NONE} 时忽略
   */
  public static CatalogAdmission of(
      String policy, String catalogCode, LocalDate asOf, Map<Long, Verdict> verdicts) {
    Mode mode = Mode.fromCode(policy);
    String code = catalogCode == null ? "" : catalogCode.trim();
    if (mode == Mode.NONE) {
      // 半配置（有编码、无策略）由规范构造器拒绝；此处只管合法的不启用形态。
      return new CatalogAdmission(Mode.NONE, code, null, Map.of());
    }
    if (code.isEmpty()) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT, "目录准入策略为 MANDATORY 但未配目录编码：不知道按哪一版目录判定，查任何一版都是猜");
    }
    if (asOf == null) {
      throw RuleGap.of(
          RuleGap.Code.CATALOG_UNDETERMINED,
          "目录「%s」无法确定判定基准日（发票日期与赔案受理日都缺失）：目录有生效期，" + "用当前版本算历史案件会系统性误判，须先补发票日期",
          code);
    }
    if (verdicts == null || verdicts.isEmpty()) {
      throw RuleGap.of(
          RuleGap.Code.CATALOG_UNRESOLVED,
          "目录「%s」在 %s 未返回任何准入结论：目录端口未接入或该版本为空表。" + "按「全部在目录内」放行等于目录形同虚设，按「全部目录外」则整案拒赔——两者都是猜",
          code,
          asOf);
    }
    return new CatalogAdmission(mode, code, asOf, verdicts);
  }

  /** 是否有明细被目录剔除（供归因与快照判定，不必再遍历 {@code verdicts}）。 */
  public long excludedCount() {
    return verdicts.values().stream().filter(v -> v == Verdict.EXCLUDED).count();
  }
}
