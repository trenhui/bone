package com.bone.tpa.push.feign.client;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.core.util.kuaitong.KTFeignConfig;
import com.bone.tpa.push.feign.request.ClaimCancelRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:51
 */
@FeignClient(name = "pukang-insurance-pushmanager", configuration = KTFeignConfig.class, contextId = "pukang-insurance-pushmanager-v1")
public interface InsurancePushManagerFeignClient {

    @PostMapping("/claimCancel")
    ApiResult<String> claimCancel(@RequestBody ClaimCancelRequest request);
}
