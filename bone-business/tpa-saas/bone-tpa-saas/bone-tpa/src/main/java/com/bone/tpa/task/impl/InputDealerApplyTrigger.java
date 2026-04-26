package com.bone.tpa.task.impl;

import com.bone.core.util.PkStringUtil;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.sdk.claim.enums.CfgAutoTypeEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * tpa跳过初审下发到saas时候，如果不是ocr录入，需要做一个异步任务申请组和人员信息
 */
@Slf4j
@Service
public class InputDealerApplyTrigger extends SyncTaskTemplate {

    public static final String pukang_source = "pukang";
    public static final String waibao_source = "waibao";
    @Autowired
    private CommonLogService commonLogService;

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    private ClaimToTpaChangeService toTpaChangeService;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private ClaimService claimService;
    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return   "input-dealer-apply";
    }

    /**
     *  xxl job 任务补偿
     */
    @XxlJob("inputDealerApplyJob")
    public void inputDealerApplyJob(){
        this.execute();
    }

    /**
     * 获取告警url
     *
     * @return
     */
    @Override
    public String getAlertUrl() {
        return dingAlertUrl;
    }

    @Override
    public String getAlertMode() {
        return AlertRobotManager.QIWEI_MODE;
    }

    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     *
     * @param task
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncOneData(SyncTask task) {
        Long claimNumber = Long.valueOf(task.getData());
        goInputManual(claimNumber);


    }

    /**
     * 进入人工录入
     * @param claimId
     */
    @Transactional(rollbackFor = Exception.class)
    public void goInputManual(Long claimId) {
        Claim exist =  claimRepository.findById(claimId);
        if( exist == null){
            throw new RuntimeException("claimId 不存在:"+claimId);
        }

        CfgAutoTypeEnum cfgAutoType =  CfgAutoTypeEnum.getByCode(exist.getCfgAutoType());
        if(cfgAutoType == null){
            cfgAutoType =CfgAutoTypeEnum.MANUAL;
        }
        if(cfgAutoType == CfgAutoTypeEnum.AUTO){
            //自动化
            auto(claimId);
        }else {
            //非自动化
            noAuto(claimId);
        }

    }

    /**
     * 不走自动化
     * @param claimId
     */
    private void noAuto(Long claimId){
        TpaSubmitClaimResult request = new   TpaSubmitClaimResult ();
        request.setClaimNumber(claimId);
        request.setEventType(Integer.valueOf(EventType.saas通知tpa初审完成不自动分配.getCode()));

        request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));
        ApiResult<Map<String,Object>>  remoteRs= tpaDataSyncFeign.submitClaimResult(request);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG,"saas通知tpa初审完成不自动分配,request:{}",request);

        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG,"saas通知tpa初审完成不自动分配,cremoteRs:{}",remoteRs);

        if(!remoteRs.isSuccess()){
            throw new RuntimeException("saas通知tpa初审完成不自动分配:"+remoteRs.getMessage());
        }
        Map<String,Object> rsData =    remoteRs.getData();
        // waibao 外包录入  pukang 普康录入
        String operatorSource = (String)rsData.get("operatorSource");
        String operatorId = (String)rsData.get("operatorId");

        String operatorName = (String)rsData.get("operatorName");
        String operatorGroupId = (String)rsData.get("operatorGroupId");
        String operatorGroupName = (String)rsData.get("operatorGroupName");
        if(StringUtils.isBlank(operatorSource)){
            throw new RuntimeException("saas通知tpa初审完成不自动分配:没有operatorSource");
        }

        Claim upDto = new Claim();
        upDto.setId(claimId);
        Claim claimCheck = claimRepository.findById(claimId);
        upDto.setBizIdentityCode(claimCheck.getBizIdentityCode());
        if( operatorSource.equals(pukang_source)){
            upDto.setStage(ClaimStatusEnum.ADDING_INPUT.getStage().getCode());
            upDto.setStatus(ClaimStatusEnum.ADDING_INPUT.getCode());
            upDto.setStatusSub(ClaimStatusEnum.ADDING_INPUT.getSubStatus());
            upDto.setOperatorUserId("");
            upDto.setOperatorUserName("");
            upDto.setOperatorOrgId("");
            upDto.setOperatorOrgName("");
        }else if( operatorSource.equals(waibao_source)){
            upDto.setStage(ClaimStatusEnum.WAIBAO_INPUTING.getStage().getCode());
            upDto.setStatus(ClaimStatusEnum.WAIBAO_INPUTING.getCode());
            upDto.setStatusSub(ClaimStatusEnum.WAIBAO_INPUTING.getSubStatus());
            upDto.setOperatorUserId(PkStringUtil.nullIfEmpty(operatorId));
            upDto.setOperatorUserName(PkStringUtil.nullIfEmpty(operatorName));
            upDto.setOperatorOrgId(PkStringUtil.nullIfEmpty(operatorGroupId));
            upDto.setOperatorOrgName(PkStringUtil.nullIfEmpty(operatorGroupName));
        }else{
            throw new RuntimeException("saas通知tpa初审完成不自动分配:没有operatorSource");
        }

        claimService.updateClaim(upDto, false, "InputDealerApplyTrigger.noAuto");
    }

    private void auto(Long claimId){
        TpaSubmitClaimResult request = new   TpaSubmitClaimResult ();
        request.setClaimNumber(claimId);

        request.setEventType(Integer.valueOf(EventType.saas通知tpa初审完成并且自动分配.getCode()));
        request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));
        ApiResult<Map<String,Object>>  remoteRs= tpaDataSyncFeign.submitClaimResult(request);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG,"saas通知tpa初审完成并且自动分配,request:{}",request);
// log.info("saas通知tpa初审完成并且自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG,"saas通知tpa初审完成并且自动分配,cremoteRs:{}",remoteRs);

        if(!remoteRs.isSuccess()){
            throw new RuntimeException("saas通知tpa初审完成并且自动分配失败:"+remoteRs.getMessage());
        }
        Map<String,Object> rsData =    remoteRs.getData();
        // waibao 外包录入  pukang 普康录入
        String operatorSource = (String)rsData.get("operatorSource");
        String operatorId = (String)rsData.get("operatorId");

        String operatorName = (String)rsData.get("operatorName");
        String operatorGroupId = (String)rsData.get("operatorGroupId");
        String operatorGroupName = (String)rsData.get("operatorGroupName");
        if(StringUtils.isBlank(operatorSource)){
            throw new RuntimeException("saas通知tpa初审完成并且自动分配失败:没有operatorSource");
        }
        Claim upDto = new Claim();
        upDto.setId(claimId);
        Claim claimCheck = claimRepository.findById(claimId);
        upDto.setBizIdentityCode(claimCheck.getBizIdentityCode());
        if(StringUtils.equals(operatorSource,waibao_source)){

            upDto.setStage(ClaimStatusEnum.WAIBAO_INPUTING.getStage().getCode());
            upDto.setStatus(ClaimStatusEnum.WAIBAO_INPUTING.getCode());
            upDto.setStatusSub(ClaimStatusEnum.WAIBAO_INPUTING.getSubStatus());
            upDto.setOperatorUserId(PkStringUtil.nullIfEmpty(operatorId));
            upDto.setOperatorUserName(PkStringUtil.nullIfEmpty(operatorName));
            upDto.setOperatorOrgId(PkStringUtil.nullIfEmpty(operatorGroupId));
            upDto.setOperatorOrgName(PkStringUtil.nullIfEmpty(operatorGroupName));
            claimService.updateClaim(upDto, false, "InputDealerApplyTrigger.auto外包录入");

        }else if(StringUtils.equals(operatorSource,pukang_source)){
            upDto.setStage(ClaimStatusEnum.PUKANG_INPUTING.getStage().getCode());
            upDto.setStatus(ClaimStatusEnum.PUKANG_INPUTING.getCode());
            upDto.setStatusSub(ClaimStatusEnum.PUKANG_INPUTING.getSubStatus());
            upDto.setOperatorUserId(PkStringUtil.nullIfEmpty(operatorId));
            upDto.setOperatorUserName(PkStringUtil.nullIfEmpty(operatorName));
            upDto.setOperatorOrgId(PkStringUtil.nullIfEmpty(operatorGroupId));
            upDto.setOperatorOrgName(PkStringUtil.nullIfEmpty(operatorGroupName));
            claimService.updateClaim(upDto, false, "InputDealerApplyTrigger.auto普康录入");

        }else {
            throw new RuntimeException("saas通知tpa初审完成并且自动分配失败,operatorSource 不合肥:"+operatorSource);
        }
    }
}
