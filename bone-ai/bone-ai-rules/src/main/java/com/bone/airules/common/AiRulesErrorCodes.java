package com.bone.airules.common;

/**
 * 规则治理稳定错误码（前端 i18n 键 + 监控聚合口径，已登记 Bone-错误码登记.md §6 与两份语言包）。
 *
 * <p>check-error-code-landing 门禁要求 application 层异常必须携带稳定业务码（errorCode 不得为 null）——
 * 裸消息串会让前端拿不到翻译键、监控只能按中文文案聚合。新增码须同批登记台账与语言包（check-i18n-sync）。
 */
public final class AiRulesErrorCodes {

  private AiRulesErrorCodes() {}

  /** 规则资产不存在（404）。 */
  public static final String NOT_FOUND = "AI_RULE_NOT_FOUND";

  /** 规则编码租户内重复（409）。 */
  public static final String CODE_DUPLICATED = "AI_RULE_CODE_DUPLICATED";

  /** 表达式编译预检失败：为空或语法错误（422）。 */
  public static final String EXPRESSION_INVALID = "AI_RULE_EXPRESSION_INVALID";

  /** 发布回归门禁未过：正/负例任一不符，逐条回显（422）。 */
  public static final String REGRESSION_FAILED = "AI_RULE_REGRESSION_FAILED";

  /** 灰度比例越界（0-100）（422）。 */
  public static final String GRAY_OUT_OF_RANGE = "AI_RULE_GRAY_OUT_OF_RANGE";

  /** 回滚目标版本不存在（404）。 */
  public static final String VERSION_NOT_FOUND = "AI_RULE_VERSION_NOT_FOUND";

  /** 回滚目标即当前版本，无需回滚（409）。 */
  public static final String ALREADY_CURRENT_VERSION = "AI_RULE_ALREADY_CURRENT_VERSION";
}
