package com.bone.tpa.sdk.adjustment.response;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(name = "adjustmentDetail", description = "理算详情")
public class InvoiceAdjustmentResponse extends TenantAbstractEntity<AdjustmentRecord, Long> {
    @Schema(description = "关联赔案ID")
    private Long relatedId;

    @Schema(description = "发票Id")
    private Long invoiceId;

    @Schema(description = "发票号")
    private String invoiceNo;

    @Schema(description = "险种名称")
    private String coverageName;

    @Schema(description = "责任Id")
    private String liabilityUuid;

    @Schema(description = "责任名称")
    private String liabilityName;

    @Schema(description = "发票金额")
    private BigDecimal invoiceAmount;

    @Schema(description = "免赔方式")
    private String deductType;

    @Schema(description = "免赔额")
    private BigDecimal deductAmount;

    @Schema(description = "控额详情")
    private String quotaDetail;

    @Schema(description = "赔付金额")
    private BigDecimal payoutAmount;

    @Schema(description = "责任账户类型")
    private String accountType;

    @Schema(description = "理算公式")
    private String formula;

    @Schema(description = "理算结论")
    private String adjustmentResult;

    @Schema(description = "发票结论")
    private String invoiceResult;

    @Schema(description = "结论明细")
    private String resultDetail;

    @Schema(description = "操作人员ID")
    private String operatorName;

}
