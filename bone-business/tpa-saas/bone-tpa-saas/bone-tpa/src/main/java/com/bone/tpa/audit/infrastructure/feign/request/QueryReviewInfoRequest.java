package com.bone.tpa.audit.infrastructure.feign.request;

import lombok.Data;

/**
 * @Author feihaiming
 *
 * @create 2025/9/22 17:52
 */
@Data
public class QueryReviewInfoRequest {
    private Long claimNo;
    private String policyNo;
    private String insuraceId;
    private ReviewRuleRequest reviewRuleDTO;
    private String userName;
}
