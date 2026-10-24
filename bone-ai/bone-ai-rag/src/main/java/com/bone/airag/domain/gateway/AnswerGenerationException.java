package com.bone.airag.domain.gateway;

import com.bone.core.exception.BizException;

/**
 * 答案生成端口故障（编排层把「这次答不了」收敛为可控拒答的载体）。
 *
 * <p><b>为什么需要它</b>：{@link AnswerGenerationPort} 的实现方（组合根里的网关适配器）调用模型时，故障来源是<b>网关治理链</b>——
 * 提示注入疑似（{@code AI_GATEWAY_INJECTION_SUSPECTED}）、租户配额超限、成本红线、输出内容不安全、provider 超时等。这些故障不应以 异常形式冲出
 * {@code RagAnswerService} 的编排边界（异常上抛会把「答不了」升级成「业务流程中断」），而是收敛为与检索拒答同构的 {@code
 * RagAnswerOutcome.unavailable(...)}：前端按 {@code errorCode} 展示、监控按拒答聚合。
 *
 * <p><b>errorCode 保真透传</b>：网关拒绝时携带的 errorCode（哪个治理层拦的）必须原样带到拒答结果——把注入拒绝伪装成
 * 「服务不可用」会让安全事件从监控里消失。故本异常持有 errorCode 字段而非统一码。
 *
 * <p>继承 {@link BizException} 与 rag 其余故障口径一致（HTTP 语义 + 稳定错误码）；适配器只需 {@link #from(BizException)}
 * 一行转换。
 */
public class AnswerGenerationException extends BizException {

  public AnswerGenerationException(int code, String message, String errorCode) {
    super(code, message, errorCode);
  }

  /**
   * 把网关（或任何底座）的 {@link BizException} 转为本端口故障，保真透传 HTTP 语义与 errorCode。
   *
   * <p>适配器实现方应捕获底座异常后经本方法转换再抛出——让 {@code RagAnswerService} 只需认识一种故障类型。
   */
  public static AnswerGenerationException from(BizException cause) {
    return new AnswerGenerationException(cause.getCode(), cause.getMessage(), cause.getErrorCode());
  }
}
