package com.bone.claim.adapter.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * 决策图流程配置写入请求。
 *
 * <p>空集合 = 恢复 canonical 图；{@code null} 由 {@code @NotNull} 在入站边界拒绝， 避免把「没传」与「显式清空」混为一谈。
 */
@Schema(description = "决策图流程配置写入请求")
public record FlowConfigReq(
    @Schema(description = "允许跳过的节点名；仅支持 AiAssess", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "skipNodes 不能为 null；传空集合表示恢复 canonical 图")
        Set<String> skipNodes) {}
