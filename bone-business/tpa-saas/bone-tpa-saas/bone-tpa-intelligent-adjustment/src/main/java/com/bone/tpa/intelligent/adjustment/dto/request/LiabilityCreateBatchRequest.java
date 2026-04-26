package com.bone.tpa.intelligent.adjustment.dto.request;

import lombok.Data;

import java.util.List;


@Data
public class LiabilityCreateBatchRequest {

    private String policyNo;

    private List<LiabilityCreateRequest> liabilityCreateRequestList;

}
