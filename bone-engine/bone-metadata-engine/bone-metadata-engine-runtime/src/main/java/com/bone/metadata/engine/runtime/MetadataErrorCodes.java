package com.bone.metadata.engine.runtime;

/**
 * 元数据运行时（模式 B 数据面）业务错误码（登记见 {@code doc/architecture/Bone-错误码登记.md} §6 {@code META_} 表）。
 *
 * <p><b>为何需要这一层，而不是直接在 {@link RuntimeRecordException} 的调用点写裸字符串</b>：业务码是 API 契约的一部分，
 * 前端、监控、告警都按它分类聚合；散落的裸字符串一旦拼错（如 {@code META_RUNTIME_RECROD_NOT_FOUND}）无任何机制发现， 且重复出现时无法单点修订（错误码登记
 * §2「可聚合」、§17「禁止把 HTTP 码与业务码混为同一个整数」）。
 *
 * <p><b>用法</b>：抛出走 {@code throw new
 * RuntimeRecordException(MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "...")}；HTTP 状态由 {@code
 * bone-metadata-server} 的 {@code GlobalExceptionHandler} 按「码 → 状态」映射提供，
 * <strong>不要</strong>在抛出点再手写状态数字：状态与码各写一处即会漂移，且不一致时无机制发现。
 *
 * <p><b>本类只承载「稳定的业务码字符串 + 语义」</b>，不承载状态（真源见 {@code GlobalExceptionHandler}），也不承载文案。
 *
 * <p><b>命名</b>：{@code META_} 为元数据域前缀，格式 {@code {DOMAIN_PREFIX}_{SNAKE_CASE_REASON}}（错误码登记 §3.1）。
 */
public final class MetadataErrorCodes {

  // ===== 运行时记录（META_RUNTIME_*）=====

  /** 运行时记录不存在（含跨租户不可见 / 软删除过滤）。 */
  public static final String RUNTIME_RECORD_NOT_FOUND = "META_RUNTIME_RECORD_NOT_FOUND";

  /** 未找到已发布的 RUNTIME 实体（catalog 侧查不到）。 */
  public static final String RUNTIME_ENTITY_NOT_FOUND = "META_RUNTIME_ENTITY_NOT_FOUND";

  /** 查询约束非法（fields/sort/q 含未建模列、格式错）。 */
  public static final String RUNTIME_INVALID_QUERY = "META_RUNTIME_INVALID_QUERY";

  /** 字段校验失败（必填缺失 / 类型不匹配）。 */
  public static final String RUNTIME_VALIDATION_FAILED = "META_RUNTIME_VALIDATION_FAILED";

  /** 唯一约束冲突（字段值已存在）。 */
  public static final String RUNTIME_DUPLICATE = "META_RUNTIME_DUPLICATE";

  /** 标识符非法（表名 / 列名不符合 {@code [a-zA-Z][a-zA-Z0-9_]*}）。 */
  public static final String RUNTIME_INVALID_IDENTIFIER = "META_RUNTIME_INVALID_IDENTIFIER";

  // ===== 数据安全（RC-008 / RC-004 · Must）=====

  /**
   * 受保护字段不可直写（实体声明 {@code state_field} 后，{@code POST}/{@code PUT} 请求体含该字段）→ 422。
   *
   * <p><b>为什么必须是显式 422 而不是静默忽略</b>：静默 drop 会让调用方误以为状态已写入，
   * 从而在「以为打款成功」的前提下继续下游业务；显式拒绝把「状态只能经状态机推进」变成可观测契约。 唯一合法入口是 {@code POST
   * …/records/{id}/actions/{actionCode}}。
   */
  public static final String RUNTIME_FIELD_PROTECTED = "META_RUNTIME_FIELD_PROTECTED";

  /**
   * 无实体级权限 {@code runtime:{entityCode}:{read|write}}（或通配 {@code runtime:*:{read|write}}）→ 403。
   *
   * <p>现状 {@code metadata:runtime:write} 等于「租户内全库 CRUD」；实体级码是「全库 CRUD」 与「按实体授权」的分界线（Runtime 数据面设计
   * §4.7）。
   */
  public static final String RUNTIME_ENTITY_FORBIDDEN = "META_RUNTIME_ENTITY_FORBIDDEN";

  /** 无动作级权限 {@code runtime:{entityCode}:{actionCode}} → 403。 */
  public static final String RUNTIME_FORBIDDEN_ACTION = "META_RUNTIME_FORBIDDEN_ACTION";

  // ===== 运行时关系（G10 · 关联/子表/附件）=====

  /**
   * 引用字段指向的目标记录不存在（同租户、未软删）→ 422。
   *
   * <p>用于写入路径阻断悬空引用：允许「类别已删除但报销单仍指向它」会把脏数据带进统计与对账，且删除目标时无拦截（对标 Directus M2O / NocoBase 关联字段的写入侧强制）。
   */
  public static final String RUNTIME_REFERENCE_NOT_FOUND = "META_RUNTIME_REFERENCE_NOT_FOUND";

  /**
   * 关系声明配置非法（缺 targetEntityCode / foreignKey 列不存在 / 子表实体无 deleted 列却要求替换）→ 500。
   *
   * <p>归500 而非 400 的理由：这是<b>建模侧</b>配置错误（validation_rules 写错），不是调用方请求错误；重试无用，需要建模者修元数据。
   */
  public static final String RUNTIME_RELATION_CONFIG_INVALID =
      "META_RUNTIME_RELATION_CONFIG_INVALID";

  // ===== 运行时状态机动作（META_RUNTIME_ACTION_*）=====

  /** 动作定义不存在（实体/动作码查不到）。 */
  public static final String RUNTIME_ACTION_NOT_FOUND = "META_RUNTIME_ACTION_NOT_FOUND";

  /** 状态机边非法（当前状态 != from_status），拒绝推进。 */
  public static final String RUNTIME_ACTION_ILLEGAL_TRANSITION =
      "META_RUNTIME_ACTION_ILLEGAL_TRANSITION";

  /** 动作入参校验失败（必填缺失 / 非法值）。 */
  public static final String RUNTIME_ACTION_INVALID_PARAMS = "META_RUNTIME_ACTION_INVALID_PARAMS";

  /** 资金类动作缺少 Idempotency-Key（防重放二次记账）。 */
  public static final String RUNTIME_ACTION_IDEMPOTENCY_REQUIRED =
      "META_RUNTIME_ACTION_IDEMPOTENCY_REQUIRED";

  /**
   * 动作执行孤儿态（副作用已发出、状态流转未完成；幂等重放被拒绝）→ 409。
   *
   * <p>P0-2 半事务防护：对真实支付网关自动重放可能造成二次出金，孤儿态只允许对账作业或人工确认后续转（Runtime 数据面设计 §4.8.1）。
   */
  public static final String RUNTIME_ACTION_ORPHANED = "META_RUNTIME_ACTION_ORPHANED";

  // ===== 运行时前置条件（META_*）=====

  /** 乐观锁版本冲突（If-Match 与当前记录不一致）。 */
  public static final String PRECONDITION_FAILED = "META_PRECONDITION_FAILED";

  private MetadataErrorCodes() {}
}
