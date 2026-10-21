package com.bone.ai.gateway.domain.gateway;

import com.bone.ai.gateway.domain.LlmResponse;

/**
 * 响应置信度抽取端口（供模型级联升档判定「廉价模型的回答够不够好」）。
 *
 * <p>抽取是领域相关且启发式的（不同 provider / prompt 的置信度承载方式不同），故做成可插拔端口而非网关内置猜测： 消费方按自身 prompt 约定覆写（如结构化 JSON
 * 字段、自评字段、外部打分服务）。默认 {@code JsonFieldConfidence} 解析 JSON {@code confidence} 字段。
 *
 * <p><b>约定</b>：返回 {@code [0,1]} 为置信度；返回<b>负数表示「无法判定」</b>——此时级联<b>不升级</b>（宁可保留廉价响应交由下游 确定性闸门 / HITL
 * 裁决，也不凭猜测升级导致双倍成本）。
 */
@FunctionalInterface
public interface ResponseConfidence {

  double confidenceOf(LlmResponse response);
}
