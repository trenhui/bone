package com.bone.tpa.intelligent.adjustment.dto.request;


import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;
import lombok.Data;

import java.util.List;

@Data
public class CreateCoverageRequest {

    private List<CoverageDTO> coverageDTOList;
}
