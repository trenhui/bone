package com.bone.tpa.sdk.adjustment.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ClaimAdjustmentResponse {

    private Long relatedId;  // 关联赔案ID

    private List<InvoiceAdjustmentResponse> invoiceResults;  // 各个发票的理算结论

    private BigDecimal payoutAmount;  // 赔付金额
    private BigDecimal publicAmount;  // 公账赔付金额
    private BigDecimal individualAmount;  // 个账赔付金额

    private String result;  // 理算结论
    private String resultDetail;  // 结论明细
}
