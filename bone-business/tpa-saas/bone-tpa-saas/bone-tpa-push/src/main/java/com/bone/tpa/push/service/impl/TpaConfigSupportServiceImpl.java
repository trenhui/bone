package com.bone.tpa.push.service.impl;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.push.feign.client.TpaServiceFeignClient;
import com.bone.tpa.push.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.push.feign.request.TpaPersonalImageQueryRequest;
import com.bone.tpa.push.feign.response.*;
import com.bone.tpa.push.service.ConfigSupportService;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:31
 */
@Slf4j
public class TpaConfigSupportServiceImpl implements ConfigSupportService {

    @Autowired private TpaServiceFeignClient tpaServiceFeignClient;

    @Override
    public CompanyInfoResponse getCompanyInfo(String companyName) {
        try {
            ApiResult<CompanyInfoResponse> apiResult = tpaServiceFeignClient.getCompanyInfo4Saas(companyName);
            if (0 != apiResult.getCode()) {
                log.error("getCompanyInfo error,{}", apiResult.getMessage());
                throw new TpaBizException("查询保司信息失败"+companyName);
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("getCompanyInfo error", ex);
            throw ex;
        }
    }


    @Override
    public SystemDictResponse getSystemDictInfo(String dictType) {
        try {
            ApiResult<SystemDictResponse> apiResult = tpaServiceFeignClient.getSystemDicts4Saas(dictType);
            if (0 != apiResult.getCode()) {
                log.error("getSystemDictInfo error,{}", apiResult.getMessage());
                throw new TpaBizException("查询字典信息失败"+dictType);
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("getSystemDictInfo error", ex);
            throw ex;
        }
    }

    @Override
    public HospitalNameCheckResponse getHospitalNameCheckResult(HospitalNameCheckRequest request) {
        try {
            ApiResult<HospitalNameCheckResponse> apiResult = tpaServiceFeignClient.checkHospitalExists4Saas(request);
            if (0 != apiResult.getCode()) {
                log.error("getHospitalNameCheckResult error,{}", apiResult.getMessage());
                throw new TpaBizException("查询药店信息失败");
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("getHospitalNameCheckResult error", ex);
            throw ex;
        }
    }

    @Override
    public Object getDutyConfig(String policyNo) {
        throw new RuntimeException("Not Implemented");
    }

    @Override
    public List<PersonalImageResponse> queryPersonalClaimImages(TpaPersonalImageQueryRequest request) {
        try {
            ApiResult<List<PersonalImageResponse>> apiResult = tpaServiceFeignClient.queryPersonalClaimImage4Saas(request);
            if (0 != apiResult.getCode()) {
                log.error("queryPersonalClaimImages error,{}", apiResult.getMessage());
                throw new TpaBizException("查询个人映像库失败");
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("queryPersonalClaimImages error", ex);
            throw ex;
        }
    }
}
