package com.bone.ai.gateway.domain.gateway;

/**
 * PII 脱敏端口（OWASP LLM02:2025 Sensitive Information Disclosure 的结构性对策）。
 *
 * <p><b>为什么放在出站口</b>：企业私有化场景里，个人信息出域到第三方模型即构成「向第三方提供个人信息」。脱敏必须是
 * <b>结构强制</b>——在网关门面对所有出站请求/落证内容统一处理，而不是指望每个业务场景自觉调用。
 *
 * <p><b>为什么用掩码而非删除</b>：把「张先生的身份证号 110101199001011234」整段删掉，模型就失去判定依据（理赔核赔要核对
 * 身份一致性）。掩码保留首尾特征位，既让模型仍能理解语义与做一致性比对，又不泄露完整值——对齐 Microsoft Presidio / AWS Comprehend 的默认「部分掩码」策略。
 *
 * <p>默认实现见 {@code infrastructure.security.RegexPiiRedactor}；使用方可 {@code @Bean} 覆盖（如接云端 Presidio /
 * DLP）。
 */
public interface PiiRedactor {

  /** 脱敏一段文本（未命中任何模式时原样返回）。 */
  String redact(String text);

  /** 脱敏开关标识（false 时应直通，不做处理）。 */
  boolean enabled();
}
