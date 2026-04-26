package com.bone.tpa.intelligent.adjustment.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "adjustmentConclusion", description = "理算结论", type = "noTime")
public class AdjustConclusion {

    @Schema(description = "理赔结论", format = "SelectCtrl")
    String payOutConclusion;

    @Schema(description = "结论原因明细")
    String conclusionDetail;
}
