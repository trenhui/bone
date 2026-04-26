package com.bone.tpa.push.service;

import com.bone.tpa.push.feign.request.QueryReviewFlagRequest;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:30
 */
public interface InsurancebizService {
    String queryReviewFlag(QueryReviewFlagRequest request);
    String queryPolicyConfig(QueryReviewFlagRequest request);
}
