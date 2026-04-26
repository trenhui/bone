package com.bone.tpa.push.service.impl;

import com.alibaba.fastjson.JSON;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.push.feign.client.InsurancebizServiceFeignClient;
import com.bone.tpa.push.feign.request.QueryReviewFlagRequest;
import com.bone.tpa.push.feign.response.QueryReviewFlagResponse;
import com.bone.tpa.push.service.InsurancebizService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:31
 */
@Service
@Slf4j
public class InsurancebizServiceImpl implements InsurancebizService {

    @Autowired private InsurancebizServiceFeignClient insurancebizServiceFeignClient;

    @Override
    public String queryReviewFlag(QueryReviewFlagRequest request) {
        log.info("queryReviewFlag,request:{}", JSON.toJSONString(request));
        try {
            QueryReviewFlagResponse apiResult = insurancebizServiceFeignClient.queryReviewFlag(request);
            log.info("queryReviewFlag,response:{}", JSON.toJSONString(apiResult));
            if (Objects.isNull(apiResult) || !Objects.equals(apiResult.getCode(), 0)) {
                log.error("queryReviewFlag error,{}", apiResult.getMessage());
                return "1";
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("queryReviewFlag error {}", JSON.toJSONString(request), ex);
            return "1";
        }
    }

    @Override
    public String queryPolicyConfig(QueryReviewFlagRequest request) {
        log.info("queryPolicyConfig,request:{}", JSON.toJSONString(request));
        try {
            ApiResult<String> apiResult = insurancebizServiceFeignClient.queryPolicyConfig(request.getPolicyNo(), request.getClaimCode());
            log.info("queryPolicyConfig,response:{}", JSON.toJSONString(apiResult));
            if (Objects.isNull(apiResult) || !Objects.equals(apiResult.getCode(), 0) || StringUtils.isBlank(apiResult.getData())) {
                log.error("queryPolicyConfig error,{}", apiResult.getMessage());
                return "0";
            }
            return apiResult.getData();
        } catch (Exception ex) {
            log.error("queryPolicyConfig error {}", JSON.toJSONString(request), ex);
            return "0";
        }
    }
}
