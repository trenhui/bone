package com.bone.claim.adapter.web.dto.response;

import java.time.Instant;

// 回流结果。replayed=true 表示同 eventId 的幂等重放，未产生新记录。
// id / claimId 用 String：平台 Long→String 契约（雪花 ID 超 2^53，以 number 返回会被静默截断）。
public record AiFeedbackEventResp(
    String id,
    String eventId,
    String claimId,
    String eventType,
    String involvement,
    String disposition,
    String reasonType,
    String scene,
    String actorId,
    Instant createdAt,
    boolean replayed) {}
