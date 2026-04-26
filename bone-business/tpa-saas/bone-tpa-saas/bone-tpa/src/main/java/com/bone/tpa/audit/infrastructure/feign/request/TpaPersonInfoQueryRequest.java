package com.bone.tpa.audit.infrastructure.feign.request;

import lombok.Data;

@Data
public class TpaPersonInfoQueryRequest {
    private Long claimNo;
    private Integer dataType;
}
