package com.bone.tpa.claim.application.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 暂未投入使用
 */
@Data
public class ClaimDuplicateRequest implements Serializable {

    @Schema(description = "待复制赔案id")
    private Long claimId;

    @Schema(description = "是否生成新批次和签收时间")
    private Boolean newBatchFlag;

    @Schema(description = "是否复制原赔案赔付金额")
    private Boolean copyAmountFlag;

    @Schema(description = "是否复制原赔案流水号")
    private Boolean copyReceiptNoFlag;

    @Schema(description = "新赔案初始状态")
    private String stage;

    @Schema(description = "处理人员")
    private String operator;

    @Schema(description = "是否复制客户签名影像")
    private Boolean copyDocumentFlag;

    @Schema(description = "复制备注")
    private String comment;

}
