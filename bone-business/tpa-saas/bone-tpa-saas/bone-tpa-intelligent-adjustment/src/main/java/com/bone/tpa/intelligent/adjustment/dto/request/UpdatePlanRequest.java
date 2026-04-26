package com.bone.tpa.intelligent.adjustment.dto.request;

import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import lombok.Data;

@Data
public class UpdatePlanRequest {
    private PlanDTO planDTO;
    private String remark;
}
