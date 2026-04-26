package com.bone.tpa.facade.feign;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.request.QuotaInfoReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @describe: 永诚个账公账 冻结/解冻接口
 * @author: shy
 * @version: 1.0
 * @date 2024/1/25 20:48
 **/
@Component
@FeignClient(name="pukang-tpapay-server")
public interface YcClaimFeignClient {

    @PostMapping(value = "/ycClaim/operateBalance", consumes = "application/json")
    ApiResult<String> operateBalance(QuotaInfoReq req);
}
