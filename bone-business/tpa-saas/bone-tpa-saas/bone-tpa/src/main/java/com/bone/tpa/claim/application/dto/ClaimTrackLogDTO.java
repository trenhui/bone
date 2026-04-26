package com.bone.tpa.claim.application.dto;


import com.bone.core.tenant.TenantAbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 操作记录对象
 */
@Data
@Schema(name = "claimTrackLog", description = "赔案操作记录")
public class ClaimTrackLogDTO extends TenantAbstractEntity<ClaimTrackLogDTO,Long> {

    @Schema(description = "关联赔案id")
    private Long relatedClaimId;

    @Schema(description = "关联赔案号")
    private String relatedClaimNo;

    @Schema(description = "当前阶段")
    private String stage;

    @Schema(description = "操作类型")
    private String type;

    @Schema(description = "分配时间")
    private Date assignTime;

    @Schema(description = "分配人员")
    private Long assigner;

    @Schema(description = "操作人员")
    private Long operator;

    @Schema(description = "完成时间")
    private Date finishTime;

    @Schema(description = "操作描述")
    private String message;

    @Schema(description = "异常原因")
    private String reason;

    @Schema(description = "操作备注")
    private String remark;

    @Schema(description = "扩展字段")
    private String extInfo;

}
