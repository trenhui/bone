package com.bone.tpa.audit.application;

import com.bone.tpa.audit.infrastructure.feign.request.QueryReviewInfoRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaHistoryClaimQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaPersonInfoQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.response.QueryReviewInfoResponse;
import com.bone.tpa.audit.infrastructure.feign.response.TpaHistoryClaimQueryResponse;
import com.bone.tpa.audit.infrastructure.feign.response.TpaPersonInfoQueryResponse;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:30
 */
public interface ConfigSupportService<T> {
    List<T> getOutWriterRecord(Long claimId);

    List<TpaHistoryClaimQueryResponse> queryTpaHistoryClaims(TpaHistoryClaimQueryRequest request);

    QueryReviewInfoResponse queryReviewInfo(QueryReviewInfoRequest request);

    TpaPersonInfoQueryResponse queryPersonInfo(TpaPersonInfoQueryRequest request);
}
