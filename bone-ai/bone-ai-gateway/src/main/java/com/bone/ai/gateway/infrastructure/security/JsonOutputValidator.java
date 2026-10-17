package com.bone.ai.gateway.infrastructure.security;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.gateway.StructuredOutputValidator;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 结构化输出校验（默认实现）：合法 JSON 校验 + 常见围栏容错提示。
 *
 * <p>零新依赖（Jackson 已在用）。只做「能不能解析成 JSON」这一层——够挡住最常见的截断 / 围栏污染； JSON Schema 强校验需引入校验库（AGENTS L3），届时覆盖本
 * bean。
 */
public class JsonOutputValidator implements StructuredOutputValidator {

  private static final Logger log = LoggerFactory.getLogger(JsonOutputValidator.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public void validate(String content, String traceId) {
    if (content == null || content.isBlank()) {
      throw new BizException(
          502, "结构化输出为空（模型未返回内容）: traceId=" + traceId, AiGatewayErrorCodes.OUTPUT_INVALID);
    }
    String trimmed = content.trim();
    // 常见故障：模型把 JSON 包在 ```json 围栏里。这里不替它剥离（避免掩盖 provider 行为），只给出可定位的提示。
    if (trimmed.startsWith("```")) {
      log.warn("结构化输出被 markdown 围栏包裹（provider 未遵守 response_format）: traceId={}", traceId);
      throw new BizException(
          502,
          "结构化输出不是裸 JSON（被 markdown 围栏包裹）: traceId=" + traceId,
          AiGatewayErrorCodes.OUTPUT_INVALID);
    }
    try {
      JsonNode node = MAPPER.readTree(trimmed);
      if (node == null || node.isMissingNode() || node.isNull()) {
        throw new BizException(
            502, "结构化输出为空节点: traceId=" + traceId, AiGatewayErrorCodes.OUTPUT_INVALID);
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new BizException(
          502,
          "结构化输出不是合法 JSON（可能截断于 max_tokens）: traceId=" + traceId + "，原因=" + e.getOriginalMessage(),
          AiGatewayErrorCodes.OUTPUT_INVALID);
    }
  }
}
