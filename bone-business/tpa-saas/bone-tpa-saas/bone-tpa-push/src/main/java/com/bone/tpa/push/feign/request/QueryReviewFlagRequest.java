package com.bone.tpa.push.feign.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class QueryReviewFlagRequest {
    private String policyNo;
    private String claimCode;
    private String payAmount;
}
