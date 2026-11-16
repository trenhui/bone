package com.bone.claim.domain.model.feedback;

// 一次「人对 AI 建议的判断」的事实快照。只含判断，不含金额 / 结论值 / PII。
// 用 record 而非让 application 直接 new 实体：领域层不依赖 adapter 的 DTO，
// 且构造参数固定，新增字段时编译器会逼调用方同步（避免静默漏列）。
public record AiFeedbackFact(
    long claimId,
    String eventId,
    String eventType,
    String traceId,
    String involvement,
    String disposition,
    String reasonType,
    String commentText,
    String modifiedFieldsJson,
    String scene,
    String actorId) {}
