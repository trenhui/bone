package com.bone.ai.gateway.common;

/**
 * 模型网关稳定错误码（前端 i18n 键 + 监控聚合口径，已登记 Bone-错误码登记.md §6 与两份语言包）。
 *
 * <p>check-error-code-landing 门禁要求 application 层异常必须携带稳定业务码（errorCode 不得为 null）——
 * 裸消息串会让前端拿不到翻译键、监控只能按中文文案聚合。新增码须同批登记台账与语言包（check-i18n-sync）。
 */
public final class AiGatewayErrorCodes {

  private AiGatewayErrorCodes() {}

  /** 成本红线命中，请求在触达模型前被短路拒绝（400）：未发起 LLM 调用、未产生任何用量。 */
  public static final String REDLINE_BLOCKED = "AI_GATEWAY_REDLINE_BLOCKED";

  /** 租户配额超限（429）：窗口内请求数已达上限，未发起 LLM 调用。 */
  public static final String QUOTA_EXCEEDED = "AI_GATEWAY_QUOTA_EXCEEDED";

  /** 结构化输出不合规（502）：非合法 JSON / 空响应 / 被 markdown 围栏包裹（可能截断于 max_tokens）。 */
  public static final String OUTPUT_INVALID = "AI_GATEWAY_OUTPUT_INVALID";

  /** 提示注入嫌疑（400）：user 输入命中注入特征（OWASP LLM01）；默认关闭，启用方自担误伤。 */
  public static final String INJECTION_SUSPECTED = "AI_GATEWAY_INJECTION_SUSPECTED";

  /**
   * provider 响应不可用（502）：无法解析 / 结构不符 / choices 为空。
   *
   * <p>2026-10-19 新增：此前这类故障只抛裸 IllegalStateException，且 provider 返回空 choices 时会被 asText("") + finish
   * 默认值伪装成「正常的空响应」——静默坏数据比显式故障更难排查。
   */
  public static final String PROVIDER_RESPONSE_INVALID = "AI_GATEWAY_PROVIDER_RESPONSE_INVALID";
}
