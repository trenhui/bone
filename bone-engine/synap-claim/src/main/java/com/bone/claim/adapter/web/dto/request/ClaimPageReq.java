package com.bone.claim.adapter.web.dto.request;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/** 待办/列表分页查询（工作台）。 */
@Schema(description = "赔案分页查询")
public record ClaimPageReq(
    @Schema(description = "状态集（空 = 待核赔 40）") List<ClaimStatus> statuses,
    @Schema(description = "来源渠道（可空）") String sourceChannel,
    @Schema(description = "页码", defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") int page,
    @Schema(description = "每页条数（默认 20，上限 100）", defaultValue = "20")
        @Min(value = 1, message = "每页至少 1 条")
        @Max(value = 100, message = "每页最多 100 条")
        int size) {}
