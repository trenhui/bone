package com.bone.tpa.audit.infrastructure.feign.client;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.audit.infrastructure.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.audit.infrastructure.feign.request.QueryReviewInfoRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaHistoryClaimQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaPersonInfoQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.response.*;
import com.bone.tpa.core.util.kuaitong.KTFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:51
 */
@FeignClient(name = "pukang-tpa-api", configuration = KTFeignConfig.class, contextId="pukang-tpa-api-v2")
public interface TpaServiceFeignClient {
    @GetMapping("/TpaOutClaim/selectOutsourcingWrite")
    ApiResult<List<ThirdWriteInfoResponse>> selectOutsourcingWrite(@RequestParam("claimNumber") Long claimNumber);

    @PostMapping("/CLaimInfoSync/queryHistoryClaimByOutInsure4Saas")
    ApiResult<List<TpaHistoryClaimQueryResponse>> queryHistoryClaimByOutInsure(@RequestBody TpaHistoryClaimQueryRequest request);

    @PostMapping("/CLaimInfoSync/queryReviewInfo4Saas")
    ApiResult<QueryReviewInfoResponse> queryReviewInfo4Saas(@RequestBody QueryReviewInfoRequest request);

    @PostMapping("/CLaimInfoSync/queryPersonInfo4Saas")
    ApiResult<TpaPersonInfoQueryResponse> queryPersonInfo4Saas(@RequestBody TpaPersonInfoQueryRequest request);
}
