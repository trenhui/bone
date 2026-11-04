package com.bone.claim.common;

/**
 * synap-claim 业务错误码常量（{@code SC_} 前缀，稳定业务码 = i18n 键）。
 *
 * <p>每个常量必须在 {@link ClaimErrors#DEFAULT_HTTP_STATUS} 登记 HTTP 状态（类加载 fail-fast）。
 */
public final class ClaimErrorCodes {

  private ClaimErrorCodes() {}

  // ===== A1 赔案 =====

  /** 案件不存在或不可见（租户隔离下的统一 404 语义，防枚举）。 */
  public static final String CLAIM_NOT_FOUND = "SC_CLAIM_NOT_FOUND";

  /** 状态冲突（当前状态不允许该动作，§6.2 合法转换表）。 */
  public static final String CLAIM_STATUS_CONFLICT = "SC_CLAIM_STATUS_CONFLICT";

  /** 乐观锁冲突（版本竞争重试 ≤3 次后仍失败）。 */
  public static final String CLAIM_CONCURRENT_CONFLICT = "SC_CLAIM_CONCURRENT_CONFLICT";

  /** 保单不存在/未生效/被保人不匹配（报案前置校验）。 */
  public static final String CLAIM_POLICY_NOT_ACTIVE = "SC_CLAIM_POLICY_NOT_ACTIVE";

  /**
   * 报案未提供任何保单标识（平台保单号与保司保单号都为空）。
   *
   * <p>400 而非 422：这是请求体缺字段（形状问题），不是"业务前提不成立"。 为什么不在请求体上给 {@code policyNo} 加
   * {@code @NotBlank}：保司来单/成员卡面只有保司自己的号，必填会把这类渠道整体挡在门外； 而"两个都空"又必须在进入金额链路之前拦住，故把约束放在应用层解析入口。
   */
  public static final String CLAIM_POLICY_KEY_REQUIRED = "SC_CLAIM_POLICY_KEY_REQUIRED";

  /**
   * 保司保单号命中多张可用保单，无法唯一确定赔案归属（团险同号多单 / 重复承保）。
   *
   * <p>422 而非 409：请求合法、操作人有权限，是"仅凭入参无法判定该记在哪张保单"这一前提不成立。 <b>不复用 {@code
   * SC_CLAIM_POLICY_NOT_ACTIVE}</b>：那会把"号码没问题、只是不唯一"报成"保单不存在"，运营会去查一个 其实存在的保单号，真正的处置（补平台号 /
   * 合单）反而被掩盖。
   */
  public static final String CLAIM_POLICY_AMBIGUOUS = "SC_CLAIM_POLICY_AMBIGUOUS";

  /**
   * 同一请求里的平台保单号与保司保单号指向不同保单。
   *
   * <p>422：两个号都必须自洽才允许收单——否则赔案记在平台号那张、渠道按保司号那张对账，属于月底才暴露、 且无法自动纠正的错记。
   */
  public static final String CLAIM_POLICY_NUMBER_CONFLICT = "SC_CLAIM_POLICY_NUMBER_CONFLICT";

  /** 出险时间不在保单有效期内。 */
  public static final String CLAIM_OUT_OF_POLICY_PERIOD = "SC_CLAIM_OUT_OF_POLICY_PERIOD";

  /** 被保人不匹配（假名化 ID 比对失败）。 */
  public static final String CLAIM_INSURED_MISMATCH = "SC_CLAIM_INSURED_MISMATCH";

  /** 拒赔五要素缺失（REJECTED 必填）。 */
  public static final String CLAIM_REJECT_REASON_REQUIRED = "SC_CLAIM_REJECT_REASON_REQUIRED";

  /** 双人复核人必须为非第一审核人（职责分离）。 */
  public static final String CLAIM_REVIEWER_CONFLICT = "SC_CLAIM_REVIEWER_CONFLICT";

  /**
   * 该赔案的个人信息主体已撤回同意（PIPL 第 15 条）：拒绝继续处理其数据。
   *
   * <p>为什么单独给码、不复用 {@code SC_CLAIM_STATUS_CONFLICT}：撤回同意是**合规事件**，必须能被单独聚合与告警
   * （"有多少笔理赔因为撤回同意而停摆"是合规月报的口径）。混进状态冲突会让它消失在一堆普通流转失败里—— 与"无依据出金被聚合成案件没理算"同一种掩盖。
   *
   * <p>为什么是 422 而不是 409：请求本身合法、操作人也有权限，是"该主体数据已不得用于新处理"这一业务前提不成立 （与 {@code
   * SC_CLAIM_POLICY_NOT_ACTIVE} 同口径）。
   */
  public static final String CLAIM_CONSENT_WITHDRAWN = "SC_CLAIM_CONSENT_WITHDRAWN";

  // ===== A2 证据 =====

  /** 材料不存在或不可见。 */
  public static final String CLAIM_EVIDENCE_NOT_FOUND = "SC_CLAIM_EVIDENCE_NOT_FOUND";

  /** 重复材料（同案件同内容哈希，§10.5 409）。 */
  public static final String CLAIM_EVIDENCE_DUPLICATED = "SC_CLAIM_EVIDENCE_DUPLICATED";

  /** 材料份数超限（单案件 ≤20 份，§7.2）。 */
  public static final String CLAIM_EVIDENCE_LIMIT_EXCEEDED = "SC_CLAIM_EVIDENCE_LIMIT_EXCEEDED";

  /** 证据已锁定（锁定后禁止回填/补录，§4.3 不变量）。 */
  public static final String CLAIM_EVIDENCE_LOCKED = "SC_CLAIM_EVIDENCE_LOCKED";

  // ===== A4/A6 责任与理算 =====

  /** 未找到有效责任配置（计划未配置或已失效，理算无法进行）。 */
  public static final String CLAIM_LIABILITY_NOT_FOUND = "SC_CLAIM_LIABILITY_NOT_FOUND";

  /**
   * 责任配置数值字段非法（赔付比例 / 多层免赔 / 额度 / 日限额等传了不可解析的脏值）。
   *
   * <p>为什么单独给码：此前 {@link com.bone.claim.application.LiabilityConfigApplicationService} 的 {@code
   * toDecimal} 对 {@link NumberFormatException} 静默返回 {@code ZERO}，"9O"/"-50" 这类脏值会被当成 0，
   * 导致<b>系统性少赔且无任何报错</b>。金融配置必须 fast-fail，故改为显式抛此码（空白/缺省仍按既有语义归 0）。
   */
  public static final String CLAIM_LIABILITY_INVALID_ARGUMENT =
      "SC_CLAIM_LIABILITY_INVALID_ARGUMENT";

  /** 理算记录不存在。 */
  public static final String CLAIM_ADJUST_NOT_FOUND = "SC_CLAIM_ADJUST_NOT_FOUND";

  /**
   * 理算结果的状态不允许作为出金依据（{@code UNFROZEN} / {@code ROLLBACKED}）。
   *
   * <p>为什么单独给码而不复用 {@code CLAIM_ADJUST_NOT_FOUND}：理算记录<b>存在</b>，金额也读得到， 问题出在「这一版理算是否还算数」—— {@code
   * UNFROZEN}（已解冻，待重算）与 {@code ROLLBACKED}（已回滚作废）都不能作为付款依据。 两者混淆会让监控把「无依据出金」聚合成「案件没理算」，恰好掩盖资金风险。
   *
   * <p>与 {@code CLAIM_ADJUST_EVIDENCE_NOT_LOCKED} 同为 422：请求本身合法，是资金前置的业务前提不成立。
   */
  public static final String CLAIM_ADJUST_STATUS_INVALID = "SC_CLAIM_ADJUST_STATUS_INVALID";

  /** 结算单不存在。 */
  public static final String CLAIM_SETTLEMENT_NOT_FOUND = "SC_CLAIM_SETTLEMENT_NOT_FOUND";

  /** 结算状态冲突（已终态不可再操作，§6.4 阶段单调）。 */
  public static final String CLAIM_SETTLEMENT_CONFLICT = "SC_CLAIM_SETTLEMENT_CONFLICT";

  // ===== A3 决策执行 =====

  /** 同案已有活跃决策执行（每案仅一个活跃执行，§4.3 不变量）。 */
  public static final String CLAIM_DECISION_ACTIVE_CONFLICT = "SC_CLAIM_DECISION_ACTIVE_CONFLICT";

  /** 案件尚无决策执行记录（轨迹回放无对象，区别于「案件不存在」）。 */
  public static final String CLAIM_DECISION_NOT_FOUND = "SC_CLAIM_DECISION_NOT_FOUND";

  // ===== A8 申诉 =====

  /** 申诉记录不存在（或已复议终态）。 */
  public static final String CLAIM_APPEAL_NOT_FOUND = "SC_CLAIM_APPEAL_NOT_FOUND";

  /**
   * 申诉超出 48h 受理时限（§6.6，《保险法》第 23/24 条口径）。
   *
   * <p>为什么单独给码而不复用 {@code COMMON_VALIDATION_FAILED}：时限过期是<b>监管口径的业务结论</b>， 不是"参数填错"。域层此前抛 {@link
   * com.bone.core.exception.DomainException}，经全局 handler 落成 400 + {@code
   * COMMON_VALIDATION_FAILED}，前端只能显示 `参数校验失败：申诉已超 48h 时限（§6.6），不可受理`——
   * 既把内部分节号泄露给柜面人员，又让"申诉窗口已关闭"和"字段格式错误"在监控里同码聚合。
   */
  public static final String CLAIM_APPEAL_EXPIRED = "SC_CLAIM_APPEAL_EXPIRED";

  /** 证据未锁定即发起理算（理算前置不满足，§4.3）。 */
  public static final String CLAIM_ADJUST_EVIDENCE_NOT_LOCKED =
      "SC_CLAIM_ADJUST_EVIDENCE_NOT_LOCKED";

  // ===== A9 后督抽检 =====

  /** 抽检单不存在（或已逻辑删除）。 */
  public static final String CLAIM_INSPECTION_NOT_FOUND = "SC_CLAIM_INSPECTION_NOT_FOUND";

  /** 抽检单状态冲突（已结论不可改写 / 同案重复发起）。 */
  public static final String CLAIM_INSPECTION_CONFLICT = "SC_CLAIM_INSPECTION_CONFLICT";

  // ===== A5 额度 =====

  /** 额度账户不存在。 */
  public static final String CLAIM_QUOTA_NOT_FOUND = "SC_CLAIM_QUOTA_NOT_FOUND";

  /** 可用额度不足（占额被拒，R-CAP 截断）。 */
  public static final String CLAIM_QUOTA_INSUFFICIENT = "SC_CLAIM_QUOTA_INSUFFICIENT";

  /** 额度状态冲突（已确认 CONFIRMED 禁止清除/重算，§5.3 硬不变量）。 */
  public static final String CLAIM_QUOTA_STATE_CONFLICT = "SC_CLAIM_QUOTA_STATE_CONFLICT";

  // ===== 配置面：保单 / 计划 / 险种 =====

  /**
   * 保单不存在（配置面查保单/计划/险种时）。
   *
   * <p>此前保单无主数据表，{@code t_claim_case.policy_no} 只是裸字符串，报案前置校验靠进程内存桩 （{@code PolicyQueryPortAdapter}
   * 的 {@code ConcurrentHashMap}），只有 {@code @Profile("dev")} 的 seeder 灌两条种子 ⇒ 非 dev
   * 环境查任何保单都返回空，<b>生产报案必然被拒</b>。
   */
  public static final String CLAIM_POLICY_NOT_FOUND = "SC_CLAIM_POLICY_NOT_FOUND";

  /** 保单号重复（同租户下 {@code policy_no} 唯一）。 */
  public static final String CLAIM_POLICY_DUPLICATED = "SC_CLAIM_POLICY_DUPLICATED";

  /**
   * 保单类型与被保人清单自相矛盾（个人保单出现多名被保人）。
   *
   * <p>为什么单独立码：个人保单的业务定义是"自然人投保、单人单账户"，其额度池必须且只能对唯一被保人开立。
   * 一旦出现多名被保人，只可能是两种情况——保单类型标错，或名单串到了别的保单。两者都会让责任额度池
   * 开到<b>不存在的人</b>头上（额度虚增、理赔时对不上人），属资金级错误。用一条可诊断的错误挡住， 比事后按流水对账要便宜得多。
   */
  public static final String CLAIM_POLICY_TYPE_CONFLICT = "SC_CLAIM_POLICY_TYPE_CONFLICT";

  /** 映射批量导入被整批拒绝：文件不可解析或超过 2000 行上限（行级错误走结果对象，不抛异常）。 */
  public static final String CLAIM_MAPPING_IMPORT_REJECTED = "SC_CLAIM_MAPPING_IMPORT_REJECTED";

  /**
   * 保单引用的业务主体（bizIdentityCode）不合法：主体不存在，或已停用/终止。
   *
   * <p>为什么单独给码：主体码是映射层的键——保单挂着不存在的主体码，收单解析会在该键下建出 一条永远没人维护的映射队列；挂着已停用主体则等于绕过「停用保司冻结映射面」的运营动作。
   * 与「参数格式错」不同：请求本身合法，是引用的主数据前提不成立，故 422。
   */
  public static final String CLAIM_BIZ_IDENTITY_INVALID = "SC_CLAIM_BIZ_IDENTITY_INVALID";

  /** 计划不存在（按 planId 查）。 */
  public static final String CLAIM_PLAN_NOT_FOUND = "SC_CLAIM_PLAN_NOT_FOUND";

  /** 计划编码重复（同保单下 {@code plan_code} 唯一）。 */
  public static final String CLAIM_PLAN_DUPLICATED = "SC_CLAIM_PLAN_DUPLICATED";

  /** 险种不存在（按 coverageId 查）。 */
  public static final String CLAIM_COVERAGE_NOT_FOUND = "SC_CLAIM_COVERAGE_NOT_FOUND";

  /** 险种编码重复（同计划下 {@code coverage_code} 唯一）。 */
  public static final String CLAIM_COVERAGE_DUPLICATED = "SC_CLAIM_COVERAGE_DUPLICATED";

  // ===== 资金对账处置 =====

  /** 对账差异不存在（或不属于本租户）。 */
  public static final String CLAIM_RECON_DIFF_NOT_FOUND = "SC_CLAIM_RECON_DIFF_NOT_FOUND";

  /**
   * 对账差异处置冲突：已处置过（处置态只允许 {@code PENDING} 单向迁移一次），或处置结论非法。
   *
   * <p>为什么"已处置"用 409 而不是 200 幂等返回：处置结论是有语义的（RESOLVED / ADJUSTED / WAIVED 代表三种不同的后续动作），
   * 第二次提交的结论可能与第一次不同，静默返回首次结果会让调用方以为"我的结论生效了"。
   */
  public static final String CLAIM_RECON_DIFF_CONFLICT = "SC_CLAIM_RECON_DIFF_CONFLICT";

  // ===== 评测样本导出 =====

  /**
   * 评测样本查询参数非法：时间窗缺失/倒置/跨度过大，或条数超出上界。
   *
   * <p>为什么单独给码而不是让 {@link IllegalArgumentException} 冒泡：全局兜底会把非受检异常翻成 <b>500
   * COMMON_INTERNAL_ERROR</b>，运维看到的是「服务器故障」而实际是调用方参数错——既误导排查， 又污染 SLI（5xx 率）。 时间窗上界（31
   * 天）与条数上界（500）是<b>防全库扫描</b>的护栏，必须显式拒绝而不是静默裁剪： 静默裁剪会让调用方以为「导出了全量」。
   */
  public static final String CLAIM_EVAL_SAMPLE_QUERY_INVALID = "SC_CLAIM_EVAL_SAMPLE_QUERY_INVALID";
}
