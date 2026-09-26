package com.bone.core.common;

/**
 * 平台公共（基础设施）错误码——**跨模块共用**的稳定业务码。
 *
 * <p><b>为何集中放 bone-core，而不是各模块各写一份</b>：无权限、资源不存在、参数校验失败、内部错误这类语义 「任何模块都可能触发」。若挂模块前缀（{@code IAM_} /
 * {@code SYS_} / {@code MD_}），会退化成 N 个模块各有一套同义码， 破坏《Bone-错误码登记》§2 的「可聚合」。集中一处还能让前端语言包的 {@code
 * errors.*} 只收录一份键。
 *
 * <p><b>与《Bone-错误码登记》的关系</b>：本类每个常量都必须出现在登记册 §6 的 {@code COMMON_} 表里； 新增码须**先登记台账再落代码**（登记册
 * §2「单一登记」）。{@code scripts/check-i18n-sync.py} 会做「代码 → 台账」差集校验。
 *
 * <p><b>命名与扫描约定</b>：必须是 {@code public static final String}（扫描脚本只认这种声明， 写成枚举或裸字面量会被判为「后端缺码」而阻断
 * PR）；值格式 {@code COMMON_{SNAKE_CASE}}。
 *
 * <p><b>不承载文案</b>：码只表达语义，展示文案在前端语言包 {@code errors.*}（i18n 方案 §3「展示分离」）。
 */
public final class CommonErrorCodes {

  /** 参数校验失败（Bean Validation / 参数缺失 / 类型错误，跨模块通用）。 */
  public static final String VALIDATION_FAILED = "COMMON_VALIDATION_FAILED";

  /** 未认证 / 令牌无效或过期。 */
  public static final String UNAUTHORIZED = "COMMON_UNAUTHORIZED";

  /** 已认证但无权限访问该资源。 */
  public static final String FORBIDDEN = "COMMON_FORBIDDEN";

  /** 资源不存在（路由缺失 / 聚合查询不到）。 */
  public static final String NOT_FOUND = "COMMON_NOT_FOUND";

  /** 资源状态冲突（唯一约束冲突、状态机不允许该操作）。 */
  public static final String CONFLICT = "COMMON_CONFLICT";

  /** 同一 Idempotency-Key 被用于不同请求体。 */
  public static final String IDEMPOTENCY_CONFLICT = "COMMON_IDEMPOTENCY_CONFLICT";

  /** 资源前置条件未满足（412，如乐观锁版本号不匹配、ETag 校验失败、状态机不允许当前操作）。 */
  public static final String PRECONDITION_FAILED = "COMMON_PRECONDITION_FAILED";

  /** 限流（Resilience4j / 网关层拒绝）。 */
  public static final String RATE_LIMITED = "COMMON_RATE_LIMITED";

  /** 未捕获的服务端异常兜底。 */
  public static final String INTERNAL_ERROR = "COMMON_INTERNAL_ERROR";

  /** HTTP 方法不被支持（405）。 */
  public static final String METHOD_NOT_ALLOWED = "COMMON_METHOD_NOT_ALLOWED";

  /** 能力未实现（501，如连接器/适配器未实现）—— 必须显式区别于 500。 */
  public static final String NOT_IMPLEMENTED = "COMMON_NOT_IMPLEMENTED";

  /** 请求体无法解析（JSON 格式错误 / 类型不匹配）。 */
  public static final String MALFORMED_REQUEST = "COMMON_MALFORMED_REQUEST";

  private CommonErrorCodes() {}
}
