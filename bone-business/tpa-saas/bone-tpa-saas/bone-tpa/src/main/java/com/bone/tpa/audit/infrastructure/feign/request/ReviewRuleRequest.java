package com.bone.tpa.audit.infrastructure.feign.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReviewRuleRequest {
    @Schema(name = "赔付总金额")
    private BigDecimal payAmountTotal = new BigDecimal(0);
    @Schema(name = "合理总金额")
    private BigDecimal reasonAmountTotal = new BigDecimal(0);
    @Schema(name = "单张发票赔付金额")
    private BigDecimal payAmountSingle = new BigDecimal(0);
    @Schema(name = "单张发票合理金额")
    private BigDecimal reasonAmountSingle = new BigDecimal(0);
    @Schema(name = "发票赔付金额为0的数量")
    private Integer zeroCompensationInvoiceCount;
    @Schema(name = "门诊发票的数量")
    private Integer outpatientInvoiceCount;
    @Schema(name = "住院发票的数量")
    private Integer hospitalizedInvoiceCount;
    @Schema(name = "购药发票的数量")
    private Integer buyMedicineInvoiceCount;
    @Schema(name = "理算责任为医疗型的数量")
    private Integer reimbursementCount;
    @Schema(name = "理算责任为津贴型的数量")
    private Integer fixedAmountCount;
    @Schema(name = "理算责任为给付型的数量")
    private Integer allowanceCount;
}
