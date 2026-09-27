package com.bone.engine.extension.studio.common;

/**
 * Studio API 稳定 errorCode，登记见 doc/architecture/Bone-错误码登记.md §EXT_。
 *
 * <p><b>「码 → HTTP 状态」的唯一真源是 {@link StudioErrors}</b>：本类只承载稳定码字符串与语义，不在抛出点手写 状态数字（错误码登记 §6）。本类的每个
 * {@code String} 常量都必须在 {@link StudioErrors} 登记状态，否则类加载即 失败（fail fast），漏登记不可能溜到运行期。
 */
public final class StudioErrorCodes {

  // —— 领域码（插件 / 扩展点生命周期）——

  /** 插件不存在（含跨租户不可见）。 */
  public static final String PLUGIN_NOT_FOUND = "EXT_PLUGIN_NOT_FOUND";

  /** 关联扩展点不存在（含跨租户不可见）。 */
  public static final String EXT_POINT_NOT_FOUND = "EXT_EXT_POINT_NOT_FOUND";

  /** 插件版本不存在，或插件尚无可用版本（回滚时）。 */
  public static final String PLUGIN_VERSION_NOT_FOUND = "EXT_PLUGIN_VERSION_NOT_FOUND";

  /** 同一插件下该版本号已存在。 */
  public static final String PLUGIN_VERSION_CONFLICT = "EXT_PLUGIN_VERSION_CONFLICT";

  /** 当前部署状态不允许该操作（未启用即发布、未部署即模拟调用等）。 */
  public static final String DEPLOY_STATE_INVALID = "EXT_DEPLOY_STATE_INVALID";

  /** 插件包非法（非 JAR/ZIP 格式、内容不可解析等）。 */
  public static final String PLUGIN_PACKAGE_INVALID = "EXT_PLUGIN_PACKAGE_INVALID";

  // —— 通用码（沿用平台统一码，同样必须登记状态）——

  /** Studio 资源不存在（扩展点/插件等通用，历史码；新增失败场景优先用上面的领域码）。 */
  public static final String RESOURCE_NOT_FOUND = "EXT_RESOURCE_NOT_FOUND";

  /** 扩展/插件状态非法（历史码）。 */
  public static final String STATE_INVALID = "EXT_STATE_INVALID";

  public static final String VALIDATION_FAILED = "COMMON_VALIDATION_FAILED";
  public static final String FORBIDDEN = "COMMON_FORBIDDEN";
  public static final String INTERNAL_ERROR = "COMMON_INTERNAL_ERROR";
  public static final String IDEMPOTENCY_CONFLICT = "COMMON_IDEMPOTENCY_CONFLICT";
  public static final String PRECONDITION_FAILED = "COMMON_PRECONDITION_FAILED";

  private StudioErrorCodes() {}
}
