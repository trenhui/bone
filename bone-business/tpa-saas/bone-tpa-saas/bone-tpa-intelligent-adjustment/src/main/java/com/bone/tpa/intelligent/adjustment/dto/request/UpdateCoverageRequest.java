package com.bone.tpa.intelligent.adjustment.dto.request;

import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;
import lombok.Data;

@Data
public class UpdateCoverageRequest {
    private CoverageDTO coverageDTO;
    private String remark;
}
