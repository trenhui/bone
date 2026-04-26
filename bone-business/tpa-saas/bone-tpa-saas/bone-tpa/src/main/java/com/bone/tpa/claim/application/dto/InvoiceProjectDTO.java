package com.bone.tpa.claim.application.dto;

import com.bone.core.domain.extension.ExtensibleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Schema(name = "invoiceProject", description = "费用项目概况")
public class InvoiceProjectDTO extends ExtensibleObject<InvoiceProjectDTO,Long> {

    @Schema(description = "关联发票id")
    private Long relatedId;

    @Schema(description = "项目名称")
    private String projectName;
    @Schema(description = "项目发票金额")
    private BigDecimal invoiceAmount;
    @Schema(description = "项目自费金额")
    private BigDecimal projectSelfPayAmount;
    @Schema(description = "项目部分自费")
    private BigDecimal projectPartSelfPayAmount;
    @Schema(description = "项目统筹金额")
    private BigDecimal poolingAmount;
    @Schema(description = "项目三方支付")
    private BigDecimal projectThirdPartyPayAmount;
    @Schema(description = "合理金额")
    private BigDecimal projectReasonableAmount;
    @Schema(description = "不合理金额")
    private BigDecimal projectUnreasonableAmount;

    private Map<String,Object> extraStore;

    //费用项目概况下的，费用项目详情。又名药品诊断详情
    @Schema(description = "费用项目详情")
    private List<InvoiceProjectItemDTO> invoiceProjectItems;
}
