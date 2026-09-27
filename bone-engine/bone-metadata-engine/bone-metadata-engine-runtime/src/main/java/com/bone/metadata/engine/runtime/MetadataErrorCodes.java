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

  // ===== 运行时前置条件（META_*）=====

  /** 乐观锁版本冲突（If-Match 与当前记录不一致）。 */
  public static final String PRECONDITION_FAILED = "META_PRECONDITION_FAILED";

  private MetadataErrorCodes() {}
}
