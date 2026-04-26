package com.bone.tpa.claim.application.dto;

import com.bone.core.tenant.TenantAbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class SignRecordTrackLogDTO extends TenantAbstractEntity<SignRecordTrackLogDTO,Long> {

    @Schema(description = "签收状态")
    private String signStatus;

    @Schema(description = "操作类型")
    private String type;

    @Schema(description = "操作描述")
    private String message;

    @Schema(description = "操作备注")
    private String remark;

    @Schema(description = "操作者")
    private String operator;
}
