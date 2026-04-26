package com.bone.tpa.facade.feign;

import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.FeignTpaConfig;
import com.bone.tpa.facade.request.*;
import com.bone.tpa.facade.vo.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

//@RequestMapping("/saas/")
//http://tpa-sys-test.pukangpay.com.cn/api/tpa
//@FeignClient(name = "pukang-tpa-api",url = "http://tpa-sys-test.pukangpay.com.cn/api/tpa",configuration = FeignTpaConfig.class)
@FeignClient(name = "pukang-tpa-api" ,configuration = FeignTpaConfig.class)
public interface TpaDataSyncFeign {

    /**
     * 同步接口
     * @param request
     * http://api.test0.pukangpay.com.cn/project/504/interface/api/69363
     * @return
     */
    @PostMapping(value = "/saas/submitClaimResult", consumes = "application/json")
    ApiResult<Map<String,Object>>  submitClaimResult(@RequestBody  TpaSubmitClaimResult request);


    /**
     * 挂起接口
     * http://api.test0.pukangpay.com.cn/project/504/interface/api/69357
     * @param request
     * @return
     */
    @PostMapping(value = "/TpaClaim/saasAddClaimHangUpReord", consumes = "application/json")
    ApiResult<Map<String,Object>> saasAddClaimHangUpReord(@RequestBody TpaHandupRequest request);

    /**
     * 解挂接口
     * @param request
     * http://api.test0.pukangpay.com.cn/project/504/interface/api/69369
     * @return
     */
    @PostMapping(value = "/transformer/releaseClaimHangUpReord", consumes = "application/json")
    ApiResult<Map<String,Object>> releaseClaimHangUpReord(@RequestBody TpaReleaseHangUpRequest request);

    /**
     * 新增log
     * 见定义
     * http://api.test0.pukangpay.com.cn/project/504/interface/api/69339
     * @param request
     * @return
     */
    @PostMapping(value = "/transformer/addLog", consumes = "application/json")
    ApiResult<Map<String,Object>> addLog(@RequestBody TpaAddLogRequest request);

    /**
     * 查询日志
     * http://api.test0.pukangpay.com.cn/project/504/interface/api/69345
     * @param claimNumber
     * @return
     */
    @GetMapping("/transformer/getLogs")
    ApiResult<List<TpaLogVO>> getLogs(@RequestParam("claimNumber") Long claimNumber);


    /**
     * 新增普康宝影像件查询接口
     * @param request
     * http://api.test0.pukangpay.com.cn/project/504/interface/api/69381
     * @return
     */
    @PostMapping(value = "/imageLibraryPersonal/getSyncClientIdCard", consumes = "application/json")
    ApiResult<PkbImageResponse> fetchPkbClaimImage(@RequestBody PkbClaimImageRequest request);


    /**
     * 新增报案信息查询接口
     * @param claimNumber
     * @return
     */
    @PostMapping(value = "/TpaClaim/saaGetReportingInformation", consumes = "application/json")
    ApiResult<GetReportingInfoResponse> getReportingInformation(@RequestBody ReportingInfoQueryRequest claimNumber);


    /**
     * 新增赔案挂起记录详情查询接口
     * http://api.test0.pukangpay.com.cn/project/504/interface/api/69375
     * @param
     * @return
     */
    @PostMapping(value = "/TpaClaim/saasGetClaimHangUpRecord", consumes = "application/json")
    public ApiResult<List<HangUpReordVO>> saasGetClaimHangUpRecord(@RequestBody HangUpReordVO request);


    /**
     * 保司影像分类查询接口
     * @param insuranceCompanyName
     * http://api.test0.pukangpay.com.cn/project/504/interface/api/69351
     * @return
     */
    @GetMapping("/ImageMap/saasGetImageMapDetail")
    ApiResult<List<InsuranceCompanyImageVO>> getImageMapDetail(@RequestParam("insuranceCompanyName") String insuranceCompanyName);


    /**
     * .新增保单特殊+特约信息查询接口
     * @param request
     * @return
     */
    @PostMapping(value = "/special/saasCheckPolicySettingRemark", consumes = "application/json")
    ApiResult<PolicySettingRemarkVO> saasCheckPolicySettingRemark(@RequestBody PolicySettingRemarkQueryRequest request);


    /**
     * 发票查重接口
     * @param request
     * @return
     */
    @PostMapping(value = "/TpaClaim/saasCheckSameBill", consumes = "application/json")
    ApiResult<List<CheckSameBillResponse>> saasGetCheckSameBillData(@RequestBody TpaCheckSameBillRequest request);


    /**
     * 复制发票，获取tpa产生的发票号接口
     * @param request
     * @return
     */
    @PostMapping(value = "/CLaimInfoSync/geneerateNewClaimNo4Saas", consumes = "application/json")
    ApiResult<NewClaimNoResponse> generateNewClaimNo4Saas(@RequestBody TpaNewClaimNoRequest request);


    /**
     * 用户信息校验接口
     * @param request
     * @return
     */
    @PostMapping(value = "/saas/queryUserGroupInfo", consumes = "application/json")
    ApiResult<UserCheckVO> queryUserGroupInfo(@RequestBody UserCheckRequest request);


    /**
     * 判断对应的赔案是否配置了处理组（基于复核组）
     * @param request
     * @return
     */
    @PostMapping(value = "/saas/queryUserGroupList", consumes = "application/json")
    ApiResult<List<Map<String,Object>>> queryUserGroupList(@RequestBody QueryUserGroupListRequest request);
}
