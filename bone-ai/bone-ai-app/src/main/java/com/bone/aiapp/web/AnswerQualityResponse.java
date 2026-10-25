package com.bone.aiapp.web;

import java.util.List;

/**
 * 回答质量评判响应（faithfulness / answerRelevance / contextPrecision + 未支撑断言明细）。
 *
 * <p>显式 DTO 而非直接透出 rag 的 {@code AnswerQuality}：API 契约与内部类型解耦， 内部加字段不破坏既有消费方（sidecar / 产品评测脚本）。
 */
public record AnswerQualityResponse(
    double faithfulness,
    double answerRelevance,
    double contextPrecision,
    List<String> unsupportedClaims) {

  public static AnswerQualityResponse of(
      double faithfulness,
      double answerRelevance,
      double contextPrecision,
      List<String> unsupportedClaims) {
    return new AnswerQualityResponse(
        faithfulness, answerRelevance, contextPrecision, List.copyOf(unsupportedClaims));
  }
}
