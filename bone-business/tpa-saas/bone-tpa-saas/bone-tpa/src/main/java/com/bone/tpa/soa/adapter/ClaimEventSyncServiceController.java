package com.bone.tpa.soa.adapter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.PageResult;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.inter.ClaimEventSyncService;
import com.bone.tpa.api.request.*;
import com.bone.tpa.api.response.ClaimPushFailResponse;
import com.bone.tpa.api.vo.IdentityTypeVO;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.sync.ClaimSyncFromTpaService;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.facade.vo.PageBizIdentityVO;
import com.bone.tpa.soa.application.AssignClaimService;
import com.bone.tpa.soa.application.BackNodeService;
import com.bone.tpa.soa.application.ClaimPushService;
import com.bone.tpa.soa.application.HangUpService;
import com.bone.tpa.soa.syncevent.SyncEvnetAction;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController("/saas/soa/sync")
public class ClaimEventSyncServiceController implements ClaimEventSyncService {
    @Autowired
    private HangUpService hangUpService;
    @Autowired
    private AssignClaimService assignClaimService;


    @Autowired
    private BackNodeService backNodeService;

    @Autowired
    private ClaimSyncFromTpaService syncFromTpaService;

    @Autowired
    private AlertRobotManager alertRobotManager;

    @Autowired
    private CommonLogService commonLogService;

    @Autowired
    private ClaimPushService claimPushService;

    @GetMapping("/testBackQuality")
    public  ApiResult<Map<String, Object>> testBackQuality(@RequestParam("claimNumber")Long claimId){
        alertRobotManager.doAlertAsyncDefault(  "测试是否可以发送");
        ApiResult<Map<String, Object>> rs = new  ApiResult<Map<String, Object>>();
        rs.setCode(0);
        return rs;
    }

    /**
     * 根据事件同步赔案信息
     *
     * @param request
     * @return map，对应不同的eventType 要封装不同的返回
     */
    @Override
    public ApiResult<Map<String, Object>> syncClaimAndChangeStatus(SyncClaimWithEventRequest request) {

        try {
            commonLogService.addLogASync(request.getClaimNumber().toString(),
                    CommonLogType.FROM_TPA_LOG, "从tpa同步数据:{}", request);
            EventType eventType =EventType.getByCode( request.getEventType());
            Map<String,SyncEvnetAction> actionMap=    SpringContextUtils.getApplicationContext().getBeansOfType(SyncEvnetAction.class);
            List<SyncEvnetAction> beanList =  actionMap.entrySet().stream().map(t->t.getValue()).collect(Collectors.toList());
            for(SyncEvnetAction action : beanList){
                if(action.getEvent() == eventType){
                    Map<String,Object> rsMap =  action.fire(request);
                    ApiResult<Map<String, Object>> rs = new  ApiResult<Map<String, Object>>();
                    rs.setData(rsMap);
                    commonLogService.addLogASync(request.getClaimNumber().toString(),
                            CommonLogType.FROM_TPA_LOG, "从tpa同步数据，返回:{}", rs);
                    return rs;
                }
            }
            throw new RuntimeException("not support action");
        } catch (Throwable e) {
            log.error("syncClaimAndChangeStatus error",e);

            ApiResult<Map<String, Object>> rs = new ApiResult<Map<String, Object>>();
            rs.setCode(-1);
            rs.setMessage(e.getMessage());
            commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG, "从tpa同步数据失败:{}", e.getMessage());
            try {
                alertRobotManager.doAlertAsyncDefault(
                        getTraceId()+" 从tpa同步数据失败,claimNumber:"+request.getClaimNumber()+",eventType :"+request.getEventType()+",error:"+e.getMessage());
            } catch (Exception ex) {

            }

            return rs;
        }
    }

    private String getTraceId(){
       return MDC.get("traceId");
    }
    /**
     * 退回某状态
     *
     * @param request
     * @return
     */
    @Override
    public ApiResult<Map<String, Object>> backToStatus(BackToStatusRequest request) {
        Map<String,Object> mp = null;
        try {
            mp = backNodeService.backToNode(request);
        } catch (Exception e) {
            log.error("commonLogService error ",e);
            commonLogService.addClaimLogAsync(request.getClaimInfo().getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                    "backToNode fail request:{},error:{}",request, e.getMessage());
            try {
                alertRobotManager.doAlertAsyncDefault(
                        getTraceId()+" 从tpa退回状态失败,claimNumber:"+request.getClaimInfo().getClaimNumber()+",eventType :"+request.getStatusEvent()+",error:"+e.getMessage());
            } catch (Exception ex) {

            }

            throw e;
        }
        ApiResult<Map<String, Object>> rs = new ApiResult<Map<String, Object>>();
        rs.setData(mp);
        return rs;
    }

    /**
     * 通知赔案分配和状态变更
     * 比如等待录入-》录入中
     *
     * @param request
     * @return
     */
    @Override
    public ApiResult<Map<String, Object>> assignClaim(AssignClaimAndSyncRequest request) {
        try {
            log.info("assignClaim request:{}",JSONObject.toJSONString(request));
            commonLogService.addLogASync(request.getClaimNumber().toString(),CommonLogType.FROM_TPA_LOG, "assignClaim,request:{}", request);
            Map<String,Object> mp = assignClaimService.assignClaim(request);
            ApiResult<Map<String, Object>> rs = new ApiResult<Map<String, Object>>();
            rs.setData(mp);
            return rs;
        } catch (Exception e) {
            log.error("assignClaim error ",e);
            commonLogService.addLogASync(request.getClaimNumber().toString(),CommonLogType.FROM_TPA_LOG, "assignClaim,error:{}", request);

            try {
                alertRobotManager.doAlertAsyncDefault(
                        getTraceId()+" 从tpa调用assignClaim失败,claimNumber:"+request.getClaimNumber()+",error:"+e.getMessage());
            } catch (Exception ex) {

            }
            throw e;
        }
    }

    /**
     * 通知赔案接挂
     *
     * @param request
     * @return
     */
    @Override
    public ApiResult<Map<String, Object>> releastHangUp(ReleaseHandUpRequest request) {

        commonLogService.addLogASync(request.getClaimNumber().toString(),
                CommonLogType.FROM_TPA_LOG, "releastHangUp,request:{}", request);
        try {
            Map<String,Object> rsMap = hangUpService.releastHangUp(request);
            ApiResult<Map<String, Object>> rs = new ApiResult<Map<String, Object>>();
            rs.setData(rsMap);
            return rs;
        } catch (Exception e) {
            log.error("releastHangUp error",e);
            try {
                alertRobotManager.doAlertAsyncDefault(
                        getTraceId()+" 从tpa解除挂起失败,claimNumber:"+request.getClaimNumber()+",error:"+e.getMessage());
            } catch (Exception ex) {

            }
           throw e;
        }
    }

    /**
     * @param insuranceCompanyName 保险公司
     * @param branchCompanyName    保险分公司
     * @param toubaoCompanyName    投保公司
     * @param policyNo             保单号
     * @return
     */
    @Override
    public ApiResult<IdentityTypeVO> checkIdentityCodeExist(String insuranceCompanyName, String branchCompanyName, String toubaoCompanyName, String policyNo) {
        PageBizIdentityVO identityVO =  syncFromTpaService.calBizIdentityVO(policyNo, toubaoCompanyName, branchCompanyName, insuranceCompanyName);
        //calBizIdentityVO
        ApiResult <IdentityTypeVO>  rs = new ApiResult();
        if( identityVO == null){
            return  rs;
        }
        IdentityTypeVO data= new IdentityTypeVO();
        data.setBizType(identityVO.getBizType());
        data.setBizIdentityCode(identityVO.getBizCode());
        rs.setData(data);
        return rs;
    }

    /**
     * 同步赔案推送状态
     *
     * @param request
     * @return
     */
    @Override
    public ApiResult<String> syncClaimPushStatus(ClaimPushBackRequest request) {
        log.info("syncClaimPushStatus request: {}", JSON.toJSONString(request));
        String returnValue = claimPushService.syncClaimPushStatus(request.getClaimNo(), request.getStatus(), request.getPushBackReason(), request.getErrorType());
        ApiResult<String>  rs = new ApiResult();
        rs.setData(returnValue);
        return rs;
    }


    @Override
    public PageResult<ClaimPushFailResponse> pageClaimPushFail(ClaimPushFailRequest request) {
        return claimPushService.pageClaimPushFail(request);
    }

}
