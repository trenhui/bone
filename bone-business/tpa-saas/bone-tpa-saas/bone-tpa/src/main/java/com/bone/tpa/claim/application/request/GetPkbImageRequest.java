package com.bone.tpa.claim.application.request;

import lombok.Data;

@Data
public class GetPkbImageRequest {

    private Long claimId;

    /**
     * 出险人(0)或者申请人(1)
     */
    private Integer personType;
}
