package com.bone.claim.domain.model.decision;

/**
 * AI 降级受控原因码：回答「这次为什么没走成 AI」。
 *
 * <p><b>为什么必须分类</b>：降级前只有一个字符串 {@code LLM_UNAVAILABLE}，它把「没配 provider」「模型输出不合规」「上游超时」
 * 三种完全不同的故障压成同一个码。三者的处置动作互斥——配环境、改提示词、扩容/重试——所以只报「不可用」等于不报。 运维只能去翻网关日志，而决策表里看不出任何线索（{@code
 * claim_decision_execution} 只留了 FALLBACK）。
 *
 * <p><b>为什么只有四个码</b>：分类必须有可靠依据，不能靠匹配异常消息文本。当前网关只透传「调用失败」这一个事实， 因此只能稳定区分：
 *
 * <ul>
 *   <li>{@link #MOCK_NOT_ALLOWED}：网关回落 Mock 替身（未配真实 provider）——<b>可通过配置自证</b>，不是故障；
 *   <li>{@link #OUTPUT_CONTRACT_VIOLATION}：结构化结论解析/字段约束不满足——改提示词或校验器，重试无用；
 *   <li>{@link #PROVIDER_TIMEOUT}：异常链里出现超时类型——扩容或调整超时，重试可能有效；
 *   <li>{@link #GATEWAY_ERROR}：其余网关异常——只作兜底，需看网关侧日志。
 * </ul>
 *
 * <p>刻意<b>不</b>造 {@code AUTH_FAILED} / {@code RATE_LIMITED}：网关尚未暴露类型化异常，只能从 message 里猜，
 * 猜错的代价是把「密钥过期」显示成「限流」，让人去扩容。待网关把这些故障做成类型化异常后在此追加枚举， 而不是先埋一个靠字符串匹配填的码。
 */
public enum AiDegradationReason {
  MOCK_NOT_ALLOWED,
  OUTPUT_CONTRACT_VIOLATION,
  PROVIDER_TIMEOUT,
  GATEWAY_ERROR
}
