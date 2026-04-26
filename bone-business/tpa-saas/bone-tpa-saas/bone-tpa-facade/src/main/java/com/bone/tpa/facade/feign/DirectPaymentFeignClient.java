package com.bone.tpa.facade.feign;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.FeignTpaConfig;
import com.bone.tpa.facade.request.InsuPersonInfoReqDTO;
import com.bone.tpa.facade.request.QueryBalanceRequest;
import com.bone.tpa.facade.vo.InsuPersonInfoRespDTO;
import com.bone.tpa.facade.vo.PeopleInfoResponse;
import com.bone.tpa.facade.vo.QueryBalanceResponse;
import com.bone.tpa.facade.vo.QueryPersonInfoV1;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 直付个账api
 *
 * 原封不动复制自tpa
 */
@Component
@FeignClient(name="pukang-account-server", configuration = FeignTpaConfig.class)//, url = "http://api.test0.pukangpay.com.cn/mock/614/")
public interface DirectPaymentFeignClient {

    /**
     * 直付余额查询
     * @param queryBalanceRequest
     * @return
     */
    @PostMapping(value = "/account/queryBalance", consumes = "application/json")
    ApiResult<List<QueryBalanceResponse>> queryAccount(QueryBalanceRequest queryBalanceRequest);


    /**
     * 人员信息查询
     * @return
     */
    @GetMapping(value = "/insuPerson/queryPersonInfoList")
    ApiResult<List<QueryPersonInfoV1>> queryPersonInfoList(@RequestParam(value = "personName",required = true) String personName,
                                                           @RequestParam(value = "personCertId",required = true) String personCertId,
                                                           @RequestParam(value = "slipCode",required = false) String slipCode);
    /**
     * 直付余额查询
     * @param
     * @return
     */
    @PostMapping(value = "/insuPerson/queryPersonInfoListByParam", consumes = "application/json")
    PeopleInfoResponse queryPersonInfoListByParam(@RequestParam(value = "personName")String personName,
                                                             @RequestParam(value = "personCertId")String personCertId,
                                                             @RequestParam(value = "slipCode")String slipCode);

    @PostMapping(value = "/insuPerson/queryPersonInfoBatch", consumes = "application/json")
    PeopleInfoResponse queryPersonInfoBatch(List<QueryBalanceRequest> queryBalanceRequests);


    //common/queryPersonInfo
    @PostMapping(value = "/common/queryPersonInfo", consumes = "application/json")
    ApiResult<List<InsuPersonInfoRespDTO>> queryPersonInfo(InsuPersonInfoReqDTO personInfoReqDTO);

}
