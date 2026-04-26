package com.bone.tpa.facade.feign;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.request.PooledAccountOperationRequest;
import com.bone.tpa.facade.vo.PooledAccountBalance;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 直付公账api
 *
 * 原封不动复制自tpa
 */
@Component
@FeignClient(name="pukang-policy-server")
public interface PooledAccountFeignClient {

    /**
     * 公共账户余额查询
     * @param slipCode
     * @return
     */
    @GetMapping(value = "/publicAccount/queryAccount")
    ApiResult<PooledAccountBalance> queryAccount(@RequestParam(value = "slipCode")String slipCode);
    /**
     * 冻结余额
     * @param operationRequest
     * @return
     */
    @PostMapping(value = "/publicAccount/freezeBalance", consumes = "application/json")
    ApiResult<String> freezeBalance(PooledAccountOperationRequest operationRequest);

    /**
     * 解冻余额
     * @param operationRequest
     * @return
     */
    @GetMapping(value = "/publicAccount/thawBalance")
    ApiResult<String> thawBalance(PooledAccountOperationRequest operationRequest);
}
