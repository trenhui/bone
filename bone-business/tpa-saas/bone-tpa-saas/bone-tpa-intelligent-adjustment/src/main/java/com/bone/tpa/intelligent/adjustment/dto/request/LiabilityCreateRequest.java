package com.bone.tpa.intelligent.adjustment.dto.request;


import lombok.Data;

@Data
public class LiabilityCreateRequest {

    private Long planId;

    private Long coverageId;

    private String liabilityName;

}
