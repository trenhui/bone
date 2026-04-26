package com.bone.tpa.claim.application.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 复制赔案请求
 */
@Data
public class ClaimCopyRequest implements Serializable {

    @Schema(description = "赔案号")
    private String claimNos;

    @Schema(description = "生成新批次和签收时间")
    private Boolean isBatchAndSignTime = true;

    @Schema(description = "原赔案赔付金额")
    private Boolean isCompensationAmount = true;

    @Schema(description = "原配案收单流水号") //永诚没有 暂时无用
    private Boolean isCopySerialNo = true;

    @Schema(description = "是否复制赔案报案号")
    private Boolean isCopyInsureClaimNo = true;

    @Schema(description = "复制赔案初始状态")
    private String newClaimStatus;

    @Schema(description = "复制后赔案处理人")
    private String newClaimOperator;

    @Schema(description = "处理人名")
    private String operatorName;

    @Schema(description = "复制备注")
    private String remark;

}
