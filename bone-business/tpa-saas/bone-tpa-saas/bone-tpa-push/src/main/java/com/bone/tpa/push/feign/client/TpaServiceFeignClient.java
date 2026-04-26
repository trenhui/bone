package com.bone.tpa.push.feign.client;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.core.util.kuaitong.KTFeignConfig;
import com.bone.tpa.push.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.push.feign.request.TpaPersonalImageQueryRequest;
import com.bone.tpa.push.feign.response.*;
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
@FeignClient(name = "pukang-tpa-api", configuration = KTFeignConfig.class, contextId = "pukang-tpa-api-v3")
public interface TpaServiceFeignClient {

    @GetMapping("/CLaimInfoSync/getCompanyInfo4Saas")
    ApiResult<CompanyInfoResponse> getCompanyInfo4Saas(@RequestParam("companyName") String companyName);

    @PostMapping("/CLaimInfoSync/checkHospitalExists4Saas")
    ApiResult<HospitalNameCheckResponse> checkHospitalExists4Saas(@RequestBody HospitalNameCheckRequest request);

    @GetMapping("/CLaimInfoSync/getSystemDicts4Saas")
    ApiResult<SystemDictResponse> getSystemDicts4Saas(@RequestParam("dicType") String dicType);

    @PostMapping("/CLaimInfoSync/queryPersonalClaimImage4Saas")
    ApiResult<List<PersonalImageResponse>> queryPersonalClaimImage4Saas(@RequestBody TpaPersonalImageQueryRequest request);
}
