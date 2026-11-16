package com.bone.claim.adapter.web.dto.response;

import com.bone.claim.domain.model.decision.TriageLimits;

/**
 * AI 配置治理快照：当前生效阈值 + 写 API 开关态。
 *
 * <p>治理页据此渲染只读 / 可写（方案 A 双闸门的服务端数据源）。写开关未配置时 {@code writeEnabled=false}，
 * 页面不得展示可提交控件，避免出现「页面可编辑但提交失败」的暗示。
 */
public record AiConfigGovernanceResp(TriageLimits limits, boolean writeEnabled) {}
