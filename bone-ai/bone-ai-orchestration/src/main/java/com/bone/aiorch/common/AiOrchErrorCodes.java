package com.bone.aiorch.common;

/**
 * HITL 编排稳定错误码（前端 i18n 键 + 监控聚合口径，已登记 Bone-错误码登记.md §6 与两份语言包）。
 *
 * <p>check-error-code-landing 门禁要求 application 层异常必须携带稳定业务码（errorCode 不得为 null）——
 * 裸消息串会让前端拿不到翻译键、监控只能按中文文案聚合。新增码须同批登记台账与语言包（check-i18n-sync）。
 */
public final class AiOrchErrorCodes {

  private AiOrchErrorCodes() {}

  /** 人工终审项不存在或已终审（404）。 */
  public static final String NOT_FOUND = "AI_HITL_NOT_FOUND";

  /** 人工终审项已裁决，重复裁决拒绝（409）。 */
  public static final String ALREADY_DECIDED = "AI_HITL_ALREADY_DECIDED";

  /** 裁决原因类型缺失：reject / modify 必带 reasonType（422，受控字典回流）。 */
  public static final String REASON_REQUIRED = "AI_HITL_REASON_REQUIRED";

  /** 终审记录租户标识非数值（500）：合规留痕不允许租户不明的处置记录，fail-closed。 */
  public static final String TENANT_NOT_NUMERIC = "AI_HITL_TENANT_NOT_NUMERIC";
}
