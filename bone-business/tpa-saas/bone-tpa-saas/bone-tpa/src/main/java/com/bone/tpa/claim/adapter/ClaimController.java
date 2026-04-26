package com.bone.tpa.claim.adapter;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.claim.application.ClaimApplicationService;
import com.bone.tpa.claim.application.converter.ClaimCopyLogConverter;
import com.bone.tpa.claim.application.dto.ClaimCopyLogDTO;
import com.bone.tpa.claim.application.dto.ClaimDTO;
import com.bone.tpa.claim.application.dto.HangUpReordDTO;
import com.bone.tpa.claim.application.dto.TpaLogDTO;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.application.request.*;
import com.bone.tpa.claim.application.response.ApplyInfo;
import com.bone.tpa.claim.application.response.ClaimDetailObject;
import com.bone.tpa.claim.application.response.HangupInfo;
import com.bone.tpa.claim.application.response.ReportCondition;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.config.NoLoginUri;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.EventOtherRequest;
import com.bone.tpa.facade.request.TpaAddLogRequest;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.ClaimCopyLog;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.biz.CommonSyncTaskBiz;
import com.bone.tpa.util.PkJsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 理赔案件控制器
 */
@Slf4j
@RestController
@RequestMapping("/tpa/claim")
public class ClaimController {
    @Autowired
    private ClaimToTpaChangeService toTpaChangeService;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    private CommonLogService commonLogService;

    @Autowired
    private ClaimTrackLogService trackLogService;

    @Resource
    protected CommonSyncTaskBiz sycJobManager;

    @Autowired
    private ClaimApplicationService claimApplicationService;

    /**
     * 获取赔案详情信息
     *
     * 用于录入、质检、审核、复核
     */
    @GetMapping("/detail")
    public Result<ClaimDetailObject> getClaimDetail(@RequestParam("id") Long id) {
        return Result.ok(claimApplicationService.getClaimDetail(id));
    }

    /**
     * 提交赔案详情信息
     *
     * 用于录入、质检、审核、复核
     */
    @PostMapping("/submit")
    public Result<ClaimDetailObject> submitClaim(@RequestBody ClaimDetailObject claimDetailObject) {
        return Result.ok(claimApplicationService.submitClaimDetail(claimDetailObject));
    }

    /**
     * 挂起赔案
     *
     * 目前会调用新tpa
     */
    @PostMapping("/hangup")
    public Result<ClaimDTO> hangUpClaim(@RequestBody ClaimHangupRequest request) {
        return Result.ok(claimApplicationService.hangUpClaim(request));
    }


    /**
     * 复核页面驳回赔案
     */
    @PostMapping("/reject")
    public Result<ClaimDTO> rejectClaim(@RequestBody ClaimRejectRequest request) {
        return Result.ok(claimApplicationService.rejectClaim(request));
    }


    /**
     * 复制赔案
     */
    @PostMapping("/copy")
    public Result<String> copyClaim(@RequestBody ClaimCopyRequest request) {
        claimApplicationService.copyClaim(request);
        return Result.ok();
    }

    /**
     * 查询赔案复制记录
     */
    @PostMapping("/querycopy")
    public Result<PageResult<ClaimCopyLogDTO>> queryClaimCopyLog(@RequestBody QueryListRequest request) {
        return Result.ok(claimApplicationService.queryClaimCopyLog(request));
    }


    /**
     * 保单特约信息/特殊信息信息查询接口
     *
     * 调用新tpa
     */
    @GetMapping("/policysetting")
    public Result<String> getPolicySettingInfo(@RequestParam("id") Long id, @RequestParam("type") Integer type) {
        return Result.ok(claimApplicationService.getPolicySettingInfo(id, type));
    }

    /**
     * 客户报案信息查询接口
     *
     * 调用新tpa
     */
    @GetMapping("/applyinfo")
    public Result<ApplyInfo> getApplyInfo(@RequestParam("id") Long id) {
        return Result.ok(claimApplicationService.getApplyInfo(id));
    }
    @GetMapping("/modifyTaskStatus")
    public Result<Boolean> modifyTaskStatus(@RequestParam("id") Long id,
                                            @RequestParam("status") Integer status,
                                            @RequestParam(value = "retryTime",required = false,
                                                    defaultValue = "4")Integer retryTime) {
        SyncTask checkExist =   sycJobManager.getById(id);
        if( checkExist == null){
            return Result.ok(Boolean.FALSE);
        }
        SyncTask updateDto = new SyncTask();
        updateDto.setId(id);
        updateDto.setRetryTimes(retryTime);
        updateDto.setStatus(status);
        sycJobManager.updateTaskStatus(updateDto);
        return Result.ok(Boolean.TRUE);
    }


    @GetMapping("/updateExtraStore")
    public Result<String> updateExtraStore(@RequestParam("id") Long id,
                                            @RequestParam("type") String type,
                                            @RequestParam("key") String key,
                                            @RequestParam("content") String content) {
        if(type.equals("claim")){
            Claim claim =  claimRepository.findById(id);
            if(claim == null){
                return   Result.error("claim not exist") ;
            }

            claim.getExtraStore();

            String newStore  =   ExtraStoreUtil.mergeExtraStore(content,claim.getExtraStore());
            Claim updto = new Claim();
            updto.setId(id);
            updto.setExtraStore(newStore);
            claimRepository.update(updto);
            return Result.ok(newStore);
        }
        if(key.equals("invoice")){

        }


        return Result.ok("success");
    }
    /**
     * 挂起信息查询接口
     * 仅用于初审页面右上角
     */
    @GetMapping("/hangupinfo")
    public Result<HangupInfo> getHangupInfo(@RequestParam("id") Long id) {
        return Result.ok(claimApplicationService.getHangupInfo(id));
    }

    /**
     * 赔案操作记录查询接口，tpa的
     */
    @GetMapping("/record")
    public Result<List<TpaLogDTO>> getClaimRecord(@RequestParam("claimNo") Long claimNo) {
        return Result.ok(claimApplicationService.getClaimRecord(claimNo));
    }

    /**
     * 赔案挂起记录查询接口，tpa的
     */
    @GetMapping("/hanguprecord")
    public Result<List<HangUpReordDTO>> getHangUpRecord(@RequestParam("claimNo") String claimNo) {
        return Result.ok(claimApplicationService.getHangUpRecord(claimNo));
    }

    /**
     * 发票查重接口，调用tpa的对应接口
     */
    @GetMapping("/checksameinvoice")
    public Result<Long> checkSameInvoice(@RequestParam("id") Long id) {
        return Result.ok(claimApplicationService.checkSameInvoice(id));
    }

    /**
     * 查询查重结果
     *
     * 存储在saas自己的系统里
     */
    @GetMapping("/checksameresult")
    public Result<String> getHint(@RequestParam("id") Long id) {
        return Result.ok(claimApplicationService.getHint(id));
    }


    /**
     * 查询报案信息
     */
    @GetMapping("/getreportclaimcondition")
    public Result<ReportCondition> getReportClaimCondition(@RequestParam("id") Long id) {
        return Result.ok(claimApplicationService.getReportClaimCondition(id));
    }


    /**
     * 查询报案信息
     */
    @PostMapping("/reportclaim")
    public Result<String> reportClaim(@RequestBody ReportCondition request) {
        return Result.ok(claimApplicationService.reportClaim(request));
    }

    /**
     * 退回节点的pageInit
     * @param claimNumber
     * @return
     */
    @NoLoginUri
    @GetMapping("/returnNodePageInit")
    public Result<Map<String,Object>> returnNodePageInit(@RequestParam("claimNumber")Long claimNumber){

        Claim claim =  claimRepository.findById(claimNumber);
        ClaimStatusEnum statusEnum = ClaimStatusEnum.getByCode(claim.getStatus(),claim.getStatusSub());
        if(statusEnum == null){
            return Result.error("claim status error");
        }
        ClaimStageEnum stageEnum =  statusEnum.getStage();
        Map<String,Object> mp = new HashMap<>();
        List<Map<String,Object>> returnStageList = PkListUtil.newArrayList();
        mp.put("returnStageList",returnStageList);
        if(stageEnum == ClaimStageEnum.AUDITING){
            //审核
            returnStageList.add(createReturnStageMap(claim,ClaimStageEnum.PRE_EXAM));
            returnStageList.add(createReturnStageMap(claim,ClaimStageEnum.SUBMITTING));
            returnStageList.add(createReturnStageMap(claim,ClaimStageEnum.INSPECTION));

        }else{
            throw new TpaBizException("不支持退回的阶段");
        }

        //告诉前端，取哪个主数据的类型（因为后面保司可能不用有变化，因此分开)
        mp.put("reasonCode","returnBackReason");

        return Result.ok(mp);
    }

    private Map<String,Object> createReturnStageMap(Claim claim,ClaimStageEnum targetStage){
        Map<String,Object> mp = new HashMap<>();
        mp.put("code",targetStage.getCode());
        mp.put("name",targetStage.getValue());
        if( targetStage == ClaimStageEnum.SUBMITTING){
            ClaimTrackLog trackLog =  trackLogService.queryLatestClaimRecord(claim.getId(), OperationTypeEnum.INPUT_MANUAL_COMPELTE,
                    OperationTypeEnum.WAIBAO_INPUT_COMPELTE);
            if(trackLog == null){
                mp.put("operaterName","system");
            }else{
                mp.put("operaterName",trackLog.getOperator());
            }
        }
        if( targetStage == ClaimStageEnum.INSPECTION){
            ClaimTrackLog trackLog =  trackLogService.queryLatestClaimRecord(claim.getId(),
                    OperationTypeEnum.QUALITY_CHECK_COMPLETE);
            if(trackLog == null){
                mp.put("operaterName","system");
            }else{
                mp.put("operaterName",trackLog.getOperator());
            }
        }

        if( targetStage == ClaimStageEnum.PRE_EXAM){
            ClaimTrackLog trackLog =  trackLogService.queryLatestClaimRecord(claim.getId(),
                    OperationTypeEnum.PRE_CHECK_MANUAL_COMPLETE);
            if(trackLog == null){
                mp.put("operaterName","system");
            }else{
                mp.put("operaterName",trackLog.getOperator());
            }
        }

        return mp;

    }

    /**
     * 节点退回
     * @param request
     * @return
     */
    @PostMapping("/returnNodeBack")
    public Result<Boolean> returnNodeBack(@RequestBody ClaimReturnRequest request) {
        claimApplicationService.returnNodeBack(request);
        return  Result.ok(Boolean.TRUE);
    }


    @NoLoginUri
    @GetMapping("/testFlowTpaInterface")
    public Result   testFlowTpaInterface(@RequestParam("claimNumber")Long claimNumber){

        Long claimId = claimNumber;

        Claim claim = claimRepository.findById(claimId);
        ClaimStatusEnum toClaimStatus = ClaimStatusEnum.Auditing;

        String assignType = "1";

        //  走初审人工分配
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);

         request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));

        request.setEventTime(System.currentTimeMillis());

        request.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        eventOtherRequest.setAssignStage(ClaimStageEnum.AUDITING.getCode());
        eventOtherRequest.setClaimStatus(Integer.valueOf(toClaimStatus.getCode()));
        eventOtherRequest.setAssignTag(true);
        eventOtherRequest.setAssignStrategy(assignType);


        if(toClaimStatus == ClaimStatusEnum.WaitAudit){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入审核等待分配");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        if(toClaimStatus == ClaimStatusEnum.Auditing){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入审核阶段");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        commonLogService.addLogASync(claimId.toString(), CommonLogType.TO_TPA_LOG,"saas通知tpa进入审核阶段:{}",request);
        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas通知tpa进入审核阶段,cremoteRs:{}", remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("saas通知tpa进入审核阶段，通知tpa失败:" + remoteRs.getMessage());
        }
        return Result.ok();
    }
}
