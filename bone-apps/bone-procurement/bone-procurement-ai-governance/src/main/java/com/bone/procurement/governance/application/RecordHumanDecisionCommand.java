package com.bone.procurement.governance.application;

/** 人工终决登记命令：AI 建议与人工终决分开记录。复核人不接受自报，由应用服务从登录态端口取（审计红线）。 */
public record RecordHumanDecisionCommand(
    String decisionId,
    String capabilityCode,
    String subjectRef,
    String aiConclusion,
    double aiConfidence,
    String finalConclusion,
    String reason,
    long reviewDurationMs) {}
