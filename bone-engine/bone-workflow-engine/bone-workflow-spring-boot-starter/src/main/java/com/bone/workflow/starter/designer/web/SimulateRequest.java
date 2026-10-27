package com.bone.workflow.starter.designer.web;

import java.util.Map;

/**
 * 发布前试运行请求体。
 *
 * @param definitionJson 定义原文（可是未发布的草稿）
 * @param sampleData 样例业务数据（按 fieldCode 键，支持 {@code a.b} 穿透）
 * @param initiatorId 模拟发起人（层级 / 自审批判定依赖它）
 * @param entityCode 业务实体编码（仅装配解析上下文，可空）
 * @param recordId 业务记录 id（同上，可空）
 */
public record SimulateRequest(
    String definitionJson,
    Map<String, Object> sampleData,
    String initiatorId,
    String entityCode,
    String recordId) {}
