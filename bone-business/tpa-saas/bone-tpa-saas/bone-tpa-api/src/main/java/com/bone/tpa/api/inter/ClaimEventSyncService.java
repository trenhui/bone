package com.bone.tpa.api.inter;

import com.bone.core.result.PageResult;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.infrastructure.log.ApiLog;
import com.bone.tpa.api.request.*;
import com.bone.tpa.api.response.ClaimPushFailResponse;
import com.bone.tpa.api.vo.IdentityTypeVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@ApiLog
@RequestMapping("/saas/soa/sync")
public interface ClaimEventSyncService {



    /**
     * 根据事件同步赔案信息
     *
     * @param request
     * @return map，对应不同的eventType 要封装不同的返回
     */
    @PostMapping("/syncClaimAndChangeStatus")
    ApiResult<Map<String,Object>> syncClaimAndChangeStatus(@RequestBody  SyncClaimWithEventRequest request);


    /**
     * 退回某状态
     * @param request
     * @return
     */
    @PostMapping("/backToStatus")
    ApiResult<Map<String,Object>> backToStatus(@RequestBody BackToStatusRequest request);


    /**
     * 通知赔案分配和状态变更
     * 比如等待录入-》录入中
     * @param request
     * @return
     */
    @PostMapping("/assignClaim")
    ApiResult<Map<String,Object>>    assignClaim(@RequestBody AssignClaimAndSyncRequest request);

    /**
     * 通知赔案接挂
     * @param request
     * @return
     */
    @PostMapping("/releastHangUp")
    ApiResult<Map<String,Object>>    releastHangUp(@RequestBody ReleaseHandUpRequest request);


    /**
     *
     * @param insuranceCompanyName  保险公司
     * @param branchCompanyName   保险分公司
     * @param toubaoCompanyName  投保公司
     * @param policyNo   保单号
     * @return
     */
    @GetMapping("/checkIdentityCodeExist")
    ApiResult<IdentityTypeVO>    checkIdentityCodeExist(@RequestParam("insuranceCompanyName") String insuranceCompanyName,
                                                        @RequestParam("branchCompanyName") String branchCompanyName,
                                                        @RequestParam("toubaoCompanyName") String toubaoCompanyName,
                                                        @RequestParam("policyNo") String policyNo
                                                );


    /**
     * 同步赔案推送状态
     *
     * @param request
     * @return
     */
    @PostMapping("/syncClaimPushStatus")
    ApiResult<String> syncClaimPushStatus(@RequestBody ClaimPushBackRequest request);

    /**
     * 分页查询推送失败
     *
     * @param request
     * @return
     */
    @PostMapping("/pageClaimPushFail")
    PageResult<ClaimPushFailResponse> pageClaimPushFail(@RequestBody ClaimPushFailRequest request);


}
