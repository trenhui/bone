package com.bone.metadata.catalog.application;

/**
 * 界面 schema 引用完整性问题码（前后端对账码：日志审计与前端规则 {@code code} 同 ID，ServiceNow policy 双跑对账思想）。
 *
 * <p>与 {@code CatalogErrorCodes} 的区别：此处是<b>校验问题码</b>（进 {@code EntityValidationIssue.code}，
 * level=ERROR 阻断发布），不是 HTTP 业务错误码。
 */
public final class UiSchemaRefCodes {

  /** 控件引用的 fieldCode 不在结构字段与 virtual 声明中。 */
  public static final String UI_REF_01 = "UI-REF-01";

  /** actionCode 不在已发布 action def 中。 */
  public static final String UI_REF_02 = "UI-REF-02";

  /** dataScope.policyCode 不在 row_policies 声明中。 */
  public static final String UI_REF_03 = "UI-REF-03";

  /** virtual 控件 fieldCode 与真实字段码碰撞。 */
  public static final String UI_REF_04 = "UI-REF-04";

  /** 条件嵌套深度超 3。 */
  public static final String UI_REF_05 = "UI-REF-05";

  /** computedFn/controlKind/verifyType 白名单外。 */
  public static final String UI_REF_06 = "UI-REF-06";

  /** tabs.value 的 $ 变量不在白名单。 */
  public static final String UI_REF_07 = "UI-REF-07";

  /** 条件 $ 变量命名空间不在白名单（tier/user/org/channel/device）。 */
  public static final String UI_REF_08 = "UI-REF-08";

  /** 变体 key 缺失/重复；规则缺 code（WARNING，前后端对账用）。 */
  public static final String UI_REF_09 = "UI-REF-09";

  private UiSchemaRefCodes() {}
}
