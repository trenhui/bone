package com.bone.studio.generator.common;

/**
 * Generator API 稳定 errorCode，登记见 doc/architecture/Bone-错误码登记.md §GEN_。
 *
 * <p><b>「码 → HTTP 状态」的唯一真源是 {@link GeneratorErrors}</b>：本类只承载稳定码字符串与语义，不在抛出点手写 状态数字。每个 {@code
 * String} 常量都必须在 {@link GeneratorErrors} 登记状态，否则类加载即失败（fail fast），漏登记不可能溜到运行期。
 */
public final class GeneratorErrorCodes {

  // —— 领域码（模板 / 生成任务 / 数据源）——

  /** 代码模板不存在（含跨租户不可见）。 */
  public static final String TEMPLATE_NOT_FOUND = "GEN_TEMPLATE_NOT_FOUND";

  /** 代码生成执行失败（模板渲染/写盘等运行时异常）。 */
  public static final String GENERATION_FAILED = "GEN_GENERATION_FAILED";

  /**
   * 租户上下文缺失：请求未携带有效租户且当前上下文取不到租户。
   *
   * <p>此前该场景被静默回落为平台租户 {@code 0L}（写入侧）或 {@code 1L}（查询侧），导致数据写进别人家或恒空。 现在一律失败关闭，不再有魔法默认值。
   */
  public static final String TENANT_CONTEXT_MISSING = "GEN_TENANT_CONTEXT_MISSING";

  // —— 通用码（沿用平台统一码，同样必须登记状态）——

  public static final String VALIDATION_FAILED = "COMMON_VALIDATION_FAILED";
  public static final String FORBIDDEN = "COMMON_FORBIDDEN";
  public static final String INTERNAL_ERROR = "COMMON_INTERNAL_ERROR";
  public static final String NOT_FOUND = "COMMON_NOT_FOUND";

  private GeneratorErrorCodes() {}
}
