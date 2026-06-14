package com.bone.engine.extension.support.config;

/**
 * 扩展点框架常量定义
 *
 * <p>包含扩展点系统中使用的各种常量值，如默认值、分隔符等
 *
 * @author renhui.trh 2023-11-1
 * @since 1.0.0
 */
public interface ExtPointConstants {
  /**
   * 默认匹配值，用于匹配所有维度值
   *
   * <p>当扩展点提供者的某个维度设置为此值时，表示匹配该维度的所有可能值
   */
  String DEFAULT_VALUE = "DEFAULT";

  /** 空字符串常量，用于表示未设置的表达式或空值 */
  String EMPTY_STRING = "";

  /**
   * 业务标识分隔符，用于拼接多维度的业务标识字符串
   *
   * <p>格式：tenantCode|bizCode|useCase|scenario
   */
  String SEPARATOR = "|";

  /** 表达式变量名 - 租户编码 */
  String EXPRESSION_VAR_TENANT_CODE = "tenantCode";

  /** 表达式变量名 - 业务编码 */
  String EXPRESSION_VAR_BIZ_CODE = "bizCode";

  /** 表达式变量名 - 用例编码 */
  String EXPRESSION_VAR_USE_CASE = "useCase";

  /** 表达式变量名 - 场景编码 */
  String EXPRESSION_VAR_SCENARIO = "scenario";

  /** 表达式变量名 - 业务上下文 */
  String EXPRESSION_VAR_CONTEXT = "context";

  /** 缓存默认大小限制 */
  int DEFAULT_CACHE_SIZE = 1000;
}
