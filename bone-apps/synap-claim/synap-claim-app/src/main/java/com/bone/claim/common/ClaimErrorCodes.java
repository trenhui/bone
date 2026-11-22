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

  /** 报案不存在或不可见（1:N 的"一"；租户隔离下统一 404 语义，防枚举）。 */
  public static final String CLAIM_REPORT_NOT_FOUND = "SC_CLAIM_REPORT_NOT_FOUND";

  /**
   * 报案已关闭，不能再建赔案（CLOSED 态分案被拒）。
   *
   * <p>409：请求合法，是"该报案是否还接受新赔案"这一业务前提不成立。与 {@link #CLAIM_STATUS_CONFLICT} 分开：
   * 监控里"报案维度封案"与"赔案状态机冲突"是两种处置动作，混码会把前者淹没在后者里。
   */
  public static final String CLAIM_REPORT_CLOSED = "SC_CLAIM_REPORT_CLOSED";

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

  /**
   * 共享保额（共保）池初始额度不一致：同一被保人同一共保代码下，多个责任的上限不同，导致池账户初始额度歧义。
   *
   * <p>共保池以 (被保人, 共保代码) 为账户键，开户接口对同键账户「首开幂等、后续忽略 limit」——若两个共保责任各自带不同
   * 责任限额，后者会被静默吞掉，池账户额度由"先发布者"决定，理赔占额对账错乱。共保池应有唯一额度真源， 本码用于 fail-closed 拦住配置矛盾。
   */
  public static final String CLAIM_SHARED_POOL_LIMIT_CONFLICT =
      "SC_CLAIM_SHARED_POOL_LIMIT_CONFLICT";

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

  // ===== AI 运行期健康度 =====

  /**
   * 运行期健康度查询参数非法：时间窗缺失 / 倒置 / 跨度超过 31 天。
   *
   * <p>与 {@link #CLAIM_EVAL_SAMPLE_QUERY_INVALID} 同一个理由——护栏必须<b>显式拒绝</b>而不是静默裁剪， 且必须有自己的码：运维看到
   * {@code SC_CLAIM_EVAL_SAMPLE_QUERY_INVALID} 会去查评测导出，而错在健康度查询， 排查方向被引错比没有码更费时间。
   */
  public static final String CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID =
      "SC_CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID";

  // ===== AI 放量阶段与错误预算 =====

  /**
   * 放量快照查询参数非法：时间窗缺失 / 倒置 / 跨度超过 31 天。
   *
   * <p>沿用「护栏显式拒绝、不静默裁剪」的口径。单独给码是因为排查方向不同——看到健康度的码会去查轨迹聚合， 而错在放量窗口，会被引到另一个端点上。
   */
  public static final String CLAIM_AI_ROLLOUT_QUERY_INVALID = "SC_CLAIM_AI_ROLLOUT_QUERY_INVALID";

  // ===== 独立盲标（G4-1 数据飞轮） =====

  /** 盲标任务不存在（或已逻辑删除 / 跨租户按不存在处理，不泄露存在性）。 */
  public static final String SC_CLAIM_BLIND_LABEL_NOT_FOUND = "SC_CLAIM_BLIND_LABEL_NOT_FOUND";

  /** 盲标任务状态冲突（重复提交 / 未提交先解盲 / 同案已有未解盲任务 / 快照序列化失败）。 */
  public static final String SC_CLAIM_BLIND_LABEL_CONFLICT = "SC_CLAIM_BLIND_LABEL_CONFLICT";

  /** 盲标载荷含 AI 痕迹键（禁区扫描命中）。单独给码：这是<b>防反馈环闸门被主动绕过</b>的信号， 与普通参数错误不同——监控里应单列，出现即说明有人在往载荷里塞 AI 建议。 */
  public static final String SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN =
      "SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN";

  /** 决策图流程配置非法（跳过节点不在白名单 / 配置 JSON 无法序列化），写入期拒绝。 */
  public static final String CLAIM_FLOW_CONFIG_INVALID = "SC_CLAIM_FLOW_CONFIG_INVALID";

  /** 标注人与本案后督抽检质检人相同（盲标已被既有印象污染，不可作为独立判定）。 */
  public static final String SC_CLAIM_BLIND_LABEL_ANNOTATOR_CONFLICT =
      "SC_CLAIM_BLIND_LABEL_ANNOTATOR_CONFLICT";

  // ===== 工作台 AI 回流 / 跨键检索 =====

  /** AI 回流事件非法：未知字段 / 非 AI_ASSISTED 裁决 / 原因码缺失或越界 / 盲标禁区 / 字段白名单命中。 */
  public static final String CLAIM_AI_FEEDBACK_INVALID = "SC_CLAIM_AI_FEEDBACK_INVALID";

  /** eventId 重放但载荷不同：不得静默覆盖，也不得让同一事件产生第二条曝光或裁决。 */
  public static final String CLAIM_AI_FEEDBACK_CONFLICT = "SC_CLAIM_AI_FEEDBACK_CONFLICT";

  /** 跨键检索参数非法：关键词长度 / 危险字符 / limit 越界；显式拒绝，不静默裁剪。 */
  public static final String CLAIM_SEARCH_KEYWORD_INVALID = "SC_CLAIM_SEARCH_KEYWORD_INVALID";

  // ===== 任务分配工作台（批 1） =====

  /** 任务不存在（含已逻辑删除 / 跨租户按不存在处理，不泄露存在性）。 */
  public static final String SC_CLAIM_TASK_NOT_FOUND = "SC_CLAIM_TASK_NOT_FOUND";

  /**
   * 任务并发冲突：同一任务被两人同时领取（乐观锁失败）或同案同类型已存在生效任务（唯一键冲突）。
   *
   * <p><b>为什么必须单独一个码而不是复用 409 通用冲突</b>：这两种失败在监控里含义完全不同——
   * 前者是"抢单撞车"（正常业务现象，计数即可），后者是"任务重复创建"（代码路径问题，需要告警）。 前端也要区分提示：「已被他人领取，请刷新」与「该案件已有在办任务」是两种不同的用户动作。
   */
  public static final String SC_CLAIM_TASK_CONFLICT = "SC_CLAIM_TASK_CONFLICT";

  // ===== AI 配置治理（批 2） =====

  /**
   * 配置放宽被拒：请求值比当前生效值更宽松。棘轮只允许相等或更严格，写入期拒绝且不改库。
   *
   * <p>注意与基线校验的区别：请求 9000 虽低于代码基线 10000，但若当前已收紧到 8000，仍是放宽，必须拒绝。
   */
  public static final String SC_CLAIM_CONFIG_RATCHET_VIOLATION =
      "SC_CLAIM_CONFIG_RATCHET_VIOLATION";

  /** 写 API 关闭或未配置：配置写请求一律拒绝，治理页回落只读（fail-closed，默认不可写）。 */
  public static final String SC_CLAIM_CONFIG_WRITE_DISABLED = "SC_CLAIM_CONFIG_WRITE_DISABLED";

  /** 配置请求非法：未知键 / 类型错误 / 越界值，写入期拒绝而不是静默忽略（配了不生效）。 */
  public static final String SC_CLAIM_CONFIG_INVALID = "SC_CLAIM_CONFIG_INVALID";

  // ===== 独立复审（批 3） =====

  /**
   * 独立复审任务不存在（或跨租户按不存在处理，不泄露存在性）。
   *
   * <p>404 而非 409：复审队列按持权人 + 批 1 任务归属 + 租户隔离过滤，取不到即为「对本操作人不存在」， 与 {@link #SC_CLAIM_TASK_NOT_FOUND}
   * 同一防枚举口径。
   */
  public static final String SC_CLAIM_REREVIEW_NOT_FOUND = "SC_CLAIM_REREVIEW_NOT_FOUND";

  /**
   * 独立复审人冲突：复审人与初审人为同一人（含空白/大小写绕过），或与登记的独立复审人不一致。
   *
   * <p><b>为什么必须单独给码而不是复用 {@link #CLAIM_REVIEWER_CONFLICT}</b>：双人复核冲突与独立复审冲突是两种不同的职责分离
   * 违规——监控里前者是「复核人=初审人」，后者是「独立复审人=初审人」，混码会让「独立复审这道防线被绕过」消失在普通复核冲突里，
   * 恰好掩盖职责分离失效这一内控风险。409：请求合法、状态正确，是「该操作人不满足职责分离」这一前提不成立。
   */
  public static final String SC_CLAIM_REREVIEWER_CONFLICT = "SC_CLAIM_REREVIEWER_CONFLICT";

  // ===== 抽检处置与交接（批 4） =====

  /**
   * 冲正前置未满足：已支付案件打回前必须先完成冲正（退款回冲），拒绝且不改任何状态。
   *
   * <p>409 而非 400：请求合法、案件状态也允许打回，缺的是「资金已冲正」这一事实前提。资金路径无签核，
   * 未冲正就打回会留出一个「账上已付、案件已打回重办」的缺口，故写入期硬拒绝、不事后补偿。
   */
  public static final String SC_CLAIM_REVERSAL_REQUIRED = "SC_CLAIM_REVERSAL_REQUIRED";

  /**
   * 代理冲突：同一被代理人存在重叠的生效代理，或代理区间非法（止不晚于起 / 空主体）。
   *
   * <p>409：同一时段至多一条生效代理是硬约束，第二条要么写入期事前查重被拒，要么并发下由生成列唯一键兜底拒绝。
   */
  public static final String SC_CLAIM_AGENT_CONFLICT = "SC_CLAIM_AGENT_CONFLICT";

  /** 抽检处置入参非法：结论非 PASS/REJECT/BACK，或 BACK 未给打回原因。 */
  public static final String SC_CLAIM_INSPECTION_ACTION_INVALID =
      "SC_CLAIM_INSPECTION_ACTION_INVALID";

  /**
   * 证据集含不清晰/不完整影像，阻断锁定（不能进入理算）。
   *
   * <p>422 而非 409：请求合法、案件状态也允许，缺的是「所有影像均可入理算」这一事实前提； 与 SC_CLAIM_ADJUST_EVIDENCE_NOT_LOCKED
   * 同族写入期硬拒，避免「看图不清却已锁定出金」。 明细（哪几张 UNCLEAR/INCOMPLETE）随 detail 返回，便于前端定位补传。
   */
  public static final String SC_CLAIM_EVIDENCE_UNCLEAR_BLOCKS_LOCK =
      "SC_CLAIM_EVIDENCE_UNCLEAR_BLOCKS_LOCK";

  /**
   * AI 能力就绪闸门未通过，自动通过（AUTO_APPROVE）被守门拦截转人工。
   *
   * <p>422 而非 409：请求合法，是「AI 部署态闸门/写开关」这一前提不成立； fail-closed，绝不因为展示层绿就放行资金。blocker 明细随 detail 返回。
   */
  public static final String SC_CLAIM_AI_GATE_BLOCKED = "SC_CLAIM_AI_GATE_BLOCKED";

  /**
   * 拒赔「证据位置」锚点格式非法（写入期拒绝）。
   *
   * <p>拒赔文书的证据位置必须可定位到证据集条目（EV-编号，可选 #p页码），否则申诉/复核无法回放。400 而非 422：这是请求入参形状非法， 与
   * CLAIM_REJECT_REASON_REQUIRED 同族；不允许「任意自由文本」混进合规文书，也不静默丢弃。
   */
  public static final String SC_CLAIM_REJECT_EVIDENCE_REF_INVALID =
      "SC_CLAIM_REJECT_EVIDENCE_REF_INVALID";

  /**
   * 受控拒赔原因码非法（写入期拒绝）。
   *
   * <p>rejectType 非字典真源 {@code ClaimDictApplicationService.REJECT_REASON_CODES} 中的码即 400。400 而非
   * 422：入参形状非法，与 CLAIM_REJECT_REASON_REQUIRED 同族；「缺失」与「非法」分不同码，便于定位是漏填还是前端字典与后端脱节。
   */
  public static final String SC_CLAIM_REJECT_REASON_INVALID = "SC_CLAIM_REJECT_REASON_INVALID";
}
