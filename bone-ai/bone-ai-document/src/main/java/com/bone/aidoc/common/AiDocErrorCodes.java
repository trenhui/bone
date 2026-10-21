package com.bone.aidoc.common;

/**
 * bone-ai-document 稳定错误码（前端 i18n 键 + 监控聚合口径，已登记 Bone-错误码登记.md §6 与两份语言包）。
 *
 * <p><b>为何从 {@code DoclingSidecarAdapter} 收敛到这里（2026-10-18）</b>：此前这四个码定义在适配器的私有常量里， 而 i18n
 * 一致性门禁（{@code check-i18n-sync}）只扫描 {@code *ErrorCodes.java} / {@code *Errors.java}——
 * 码散落在适配器中意味着**台账登记与代码实际字符串的一致性从未被校验**（假安全：拼错一个字符门禁也发现不了）。 收敛到本类是错误码单一真源的落地：门禁扫得到，漂移才拦得住。
 */
public final class AiDocErrorCodes {

  private AiDocErrorCodes() {}

  /** 文档超过大小上限（413）：边读边计数超限即拒，避免全量读入耗尽内存。 */
  public static final String TOO_LARGE = "AI_DOC_TOO_LARGE";

  /** 文档页数超过上限（413）。 */
  public static final String TOO_MANY_PAGES = "AI_DOC_TOO_MANY_PAGES";

  /** 解析 sidecar 不可用（502）：连接失败 / 重试耗尽 / 响应契约不符。 */
  public static final String SIDECAR_UNAVAILABLE = "AI_DOC_SIDECAR_UNAVAILABLE";

  /** 文档读取失败（400）：输入流为空 / 读取异常。 */
  public static final String READ_FAILED = "AI_DOC_READ_FAILED";

  /**
   * OCR / 视觉识别服务不可用（502）：重试耗尽 / 非 2xx / 响应契约不符 / 调用被中断。
   *
   * <p>2026-10-21 补齐：HttpOcrClient 的抛出点与台账（Bone-错误码登记.md §6）均已就位，仅缺本常量定义——缺此常量则该模块无法编译。
   */
  public static final String OCR_PROVIDER_FAIL = "AI_DOC_OCR_PROVIDER_FAIL";
}
