package com.bone.tpa.audit.application.impl;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.audit.application.ConfigSupportService;
import com.bone.tpa.audit.infrastructure.feign.client.TpaServiceFeignClient;
import com.bone.tpa.audit.infrastructure.feign.request.QueryReviewInfoRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaHistoryClaimQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaPersonInfoQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.response.QueryReviewInfoResponse;
import com.bone.tpa.audit.infrastructure.feign.response.ThirdWriteInfoResponse;
import com.bone.tpa.audit.infrastructure.feign.response.TpaHistoryClaimQueryResponse;
import com.bone.tpa.audit.infrastructure.feign.response.TpaPersonInfoQueryResponse;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:31
 */
@Service
@Slf4j
public class TpaConfigSupportServiceImpl implements ConfigSupportService<ThirdWriteInfoResponse> {

    @Autowired private TpaServiceFeignClient tpaServiceFeignClient;

    @Override
    public List<ThirdWriteInfoResponse> getOutWriterRecord(Long claimId) {
        try {
            ApiResult<List<ThirdWriteInfoResponse>> apiResult = tpaServiceFeignClient.selectOutsourcingWrite(claimId);
            if (0 != apiResult.getCode()) {
                log.error("getOutWriterRecord error,{}", apiResult.getMessage());
                throw new TpaBizException("查询外包录入信息失败"+claimId);
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("getOutWriterRecord error", ex);
            throw ex;
        }
    }

    @Override
    public List<TpaHistoryClaimQueryResponse> queryTpaHistoryClaims(TpaHistoryClaimQueryRequest request) {
        try {
            ApiResult<List<TpaHistoryClaimQueryResponse>> apiResult = tpaServiceFeignClient.queryHistoryClaimByOutInsure(request);
            if (0 != apiResult.getCode()) {
                log.error("queryTpaHistoryClaims error,{}", apiResult.getMessage());
                throw new TpaBizException("查询历史案件失败");
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("queryTpaHistoryClaims error", ex);
            throw ex;
        }
    }

    @Override
    public TpaPersonInfoQueryResponse queryPersonInfo(TpaPersonInfoQueryRequest request) {
        try {
            ApiResult<TpaPersonInfoQueryResponse> apiResult = tpaServiceFeignClient.queryPersonInfo4Saas(request);
            if (0 != apiResult.getCode()) {
                log.error("queryPersonInfo error,{}", apiResult.getMessage());
                throw new TpaBizException("查询个人信息失败");
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("queryPersonInfo error", ex);
            throw ex;
        }
    }

    public QueryReviewInfoResponse queryReviewInfo(QueryReviewInfoRequest request) {
        ApiResult<QueryReviewInfoResponse> apiResult = tpaServiceFeignClient.queryReviewInfo4Saas(request);
        if (!apiResult.isSuccess()) {
            log.error("queryReviewInfo error,{}", apiResult.getMessage());
            throw new TpaBizException("remote queryReviewInfo error : " + apiResult.getMessage());
        }
        return apiResult.getData();

    }
}
