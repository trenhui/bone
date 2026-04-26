package com.bone.tpa.intelligent.adjustment.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(name = "adjustmentResult", description = "理算结果", type = "noTime")
public class AdjustResult {

    @Schema(description = "赔案赔付金额")
    BigDecimal payOutAmount;

    @Schema(description = "公账赔付金额")
    BigDecimal publicAmount;

    @Schema(description = "个账赔付金额")
    BigDecimal privateAmount;
}
