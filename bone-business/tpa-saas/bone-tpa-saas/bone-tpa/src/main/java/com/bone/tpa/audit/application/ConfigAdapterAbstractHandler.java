package com.bone.tpa.audit.application;

import com.bone.tpa.audit.application.request.HistoryClaimQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.audit.infrastructure.feign.request.QueryReviewInfoRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaPersonInfoQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.response.*;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:38
 */
public abstract class ConfigAdapterAbstractHandler {
    public abstract List<ThirdWriteInfoResponse> handleThirdWriteInfo(Long claimId);
    public abstract List<TpaHistoryClaimQueryResponse> handleHistoryClaimQuery(HistoryClaimQueryRequest request);
    public abstract QueryReviewInfoResponse handleQueryReviewInfo(QueryReviewInfoRequest request);

    public abstract void pushClaim(Long claimNo);

    public abstract TpaPersonInfoQueryResponse queryPersonInfo(TpaPersonInfoQueryRequest request);
}
