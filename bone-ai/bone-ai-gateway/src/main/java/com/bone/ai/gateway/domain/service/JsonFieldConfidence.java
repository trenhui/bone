package com.bone.ai.gateway.domain.service;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.gateway.ResponseConfidence;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认置信度抽取：从响应文本解析 JSON {@code confidence} 字段（{@code [0,1]}）。
 *
 * <p>响应非 JSON / 无 {@code confidence} 字段 / 数值越界时返回 <b>-1（无法判定）</b>——级联据此不升级，绝不误升。 与 ClaimService
 * 的结构化置信度解析同源；需要别的置信度约定（自评字段 / 外部打分）时以 {@code @Bean ResponseConfidence} 覆盖。
 */
public class JsonFieldConfidence implements ResponseConfidence {

  private static final Pattern CONFIDENCE_PATTERN =
      Pattern.compile("\"confidence\"\\s*:\\s*(-?\\d+(\\.\\d+)?)");

  @Override
  public double confidenceOf(LlmResponse response) {
    if (response == null || response.content() == null) {
      return -1;
    }
    Matcher m = CONFIDENCE_PATTERN.matcher(response.content());
    if (m.find()) {
      try {
        double v = Double.parseDouble(m.group(1));
        return (v >= 0 && v <= 1) ? v : -1;
      } catch (NumberFormatException ignored) {
        return -1;
      }
    }
    return -1;
  }
}
