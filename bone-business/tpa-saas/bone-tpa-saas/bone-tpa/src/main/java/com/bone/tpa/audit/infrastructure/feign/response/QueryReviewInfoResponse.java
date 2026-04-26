package com.bone.tpa.audit.infrastructure.feign.response;

import lombok.Data;

/**
 * @Author feihaiming
 *
 * @create 2025/9/22 17:54
 */
@Data
public class QueryReviewInfoResponse {
    private Boolean mustReview;
    private String msg;
}
