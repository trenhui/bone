package com.bone.tpa.push.feign.client;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.core.util.kuaitong.KTFeignConfig;
import com.bone.tpa.push.feign.request.QueryReviewFlagRequest;
import com.bone.tpa.push.feign.response.QueryReviewFlagResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:51
 */
@FeignClient(name = "pukang-insurancebiz-server", configuration = KTFeignConfig.class, contextId = "pukang-insurancebiz-server-v1")
public interface InsurancebizServiceFeignClient {

    @PostMapping("/policyMode/queryReviewFlag")
    QueryReviewFlagResponse queryReviewFlag(@RequestBody QueryReviewFlagRequest request);
    @GetMapping("/policyMode/queryPolicyConfig")
    ApiResult<String> queryPolicyConfig(@RequestParam("policyNo") String policyNo, @RequestParam("claimCode") String claimCode);
}
