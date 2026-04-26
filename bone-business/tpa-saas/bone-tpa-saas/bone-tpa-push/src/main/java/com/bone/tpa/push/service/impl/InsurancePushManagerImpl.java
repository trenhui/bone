package com.bone.tpa.push.service.impl;

import com.alibaba.fastjson.JSON;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.push.feign.client.InsurancePushManagerFeignClient;
import com.bone.tpa.push.feign.request.ClaimCancelRequest;
import com.bone.tpa.push.service.InsurancePushManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:31
 */
@Service
@Slf4j
public class InsurancePushManagerImpl implements InsurancePushManager {

    @Autowired private InsurancePushManagerFeignClient insurancePushManagerFeignClient;

    @Override
    public String claimCancel(ClaimCancelRequest request) {
        log.info("claimCancel,request:{}", JSON.toJSONString(request));
        try {
            ApiResult<String> apiResult = insurancePushManagerFeignClient.claimCancel(request);
            log.info("claimCancel,response:{}", JSON.toJSONString(apiResult));
            if (Objects.isNull(apiResult) || !Objects.equals(apiResult.getCode(), 0)) {
                log.error("claimCancel error,{}", apiResult.getMessage());
                return "0";
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("claimCancel error {}", JSON.toJSONString(request), ex);
            return "0";
        }
    }
}
