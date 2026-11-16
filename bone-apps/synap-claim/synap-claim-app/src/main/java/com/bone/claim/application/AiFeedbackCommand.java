package com.bone.claim.application;

import java.util.List;
import java.util.Map;

// application 层命令。adapter 的 DTO 不进业务层（分层约束），
// unknownFields 一并带进来，让「夹带金额字段」在业务规则里被判死。
public record AiFeedbackCommand(
    String eventId,
    String eventType,
    String traceId,
    String involvement,
    String scene,
    String disposition,
    String reasonCode,
    String comment,
    List<String> modifiedFields,
    Map<String, Object> unknownFields) {}
