package com.bone.tpa.intelligent.adjustment.model;

import com.bone.tpa.sdk.adjustment.response.InvoiceAdjustmentResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 理算信息
 * 分为三块
 */
@Data
public class AdjustmentInfo {

    @Schema(description = "理算过程信息")
    List<InvoiceAdjustmentResponse> adjustmentDetailList = new ArrayList<>();

    @Schema(description = "理算结果信息")
    AdjustResult adjustmentResult;

    @Schema(description = "理算结论信息")
    AdjustConclusion adjustConclusion;
}
