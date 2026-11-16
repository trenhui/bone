package com.bone.claim.adapter.web.dto.response;

// 服务端采纳率口径：曝光为分母、裁决为分子，30 天滚动窗口。
// serverSide 恒 true，用来和前端「浏览器会话内样本」明确区分。
public record AiFeedbackSummaryResp(
    long impressions,
    long dispositions,
    long adopts,
    long rejects,
    long modifies,
    int windowDays,
    boolean serverSide) {}
