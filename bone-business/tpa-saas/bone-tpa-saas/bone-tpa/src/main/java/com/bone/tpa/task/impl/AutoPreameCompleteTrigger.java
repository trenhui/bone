package com.bone.tpa.task.impl;

import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.*;
import com.bone.tpa.claim.flow.jump.InputFlowFireService;
import com.bone.tpa.claim.flow.stage.PreCheckStageService;
import com.bone.tpa.claim.flow.trigger.PrecheckCompleteTrigger;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.sdk.vo.PreCheckConfigVO;
import com.bone.tpa.util.PkJsonUtil;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

/**
 * 自动化初审完成的trigger
 */
@Slf4j
@Service
public class AutoPreameCompleteTrigger extends SyncTaskTemplate {
    @Autowired
    private CommonLogService commonLogService;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    private ClaimTrackLogService trackLogService;
    @Autowired
    private ClaimFlowConfigBiz claimFlowConfigBiz;
    @Autowired
    private InputFlowFireService inputFlowFireService;

    @Autowired
    private ClaimService claimService;



    @Autowired
    private PreCheckStageService preCheckDealerApply;
    @XxlJob("autoPreameCompleteTrigger")
    public void autoPreameCompleteTriggerXxljob(){
        this.execute();
    }

    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "AutoPreameCompleteTrigger";
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
     * todo 朱子元 后面把内容拉出去加事务
     *
     * @param task
     */
    @Override
    public void syncOneData(SyncTask task) {
        Long claimId = Long.valueOf(task.getData());

        Claim claim =  claimRepository.findById(claimId);
        // 记录traceLog
        trackLogService.claimRecord(claim,"system",null,null,
                OperationTypeEnum.PRECHECK_AUTO,null,"自动化初审完成");
        dealAllSaas(claimId,task);
        /*
        if( claim.getCfgBizType() == 2){
            //saas仅录入
            dealSaasIput(task,claimId);
        }else{
            //helei todo
            dealAllSaas(claimId,task);
        }*/


    }


    private void dealAllSaas(Long claimId,SyncTask task){
        Claim claim =  claimRepository.findById(claimId);
        ClaimFlowConfigVO flowConfigVO =  claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        //初审配置节点
        PreCheckConfigVO preCheckConfigVO = flowConfigVO.getPreCheckConfigVO();
        /**
         * 0 先自动分类再人工确认
         * 1 仅自动分类
         * 2 仅人工分类
         */
        String categoryType = preCheckConfigVO.getCategoryType();


        if(StringUtils.equals("1",categoryType)){
            //1 不走人工，如果校验成功，直接进入下一步，否则则走人工
            boolean checkPass = true;
            Map<String,String>  checkMap =     preCheckDealerApply.preCheckPassValid(claim,task.getCreateTime());
            if(!checkMap.isEmpty()){
                checkPass = false;
            }

            if(checkPass){
                //校验成功，走到下一步
                Claim upDto = new Claim();
                upDto.setId(claimId);

                claimService.setStatusField(upDto,ClaimStatusEnum.COMPLETE_PRE_ADUIT);
                upDto.setPreExamOperatorName("system");
                upDto.setPreExamPassTime(new Date());
                claimService.clearOperator(upDto);
                claimService.updateClaim(upDto, false, "dealAllSaas");
                //进入下个节点
                //推进流程
                SpringContextUtils.getBean(PrecheckCompleteTrigger.class).addJobAndTryFire(
                        PkJsonUtil.buildPkJson(
                                "claimNumber",claim.getId().toString(),
                                "operator", "system"
                        ),3,3
                );

            }else {
                //走人工
                preCheckDealerApply.preCheckDealerApply(claim,task.getCreateTime());
            }

        }else{
            //走tpa分配和同步数据
            ClaimStatusEnum toStatus =   preCheckDealerApply.preCheckDealerApply(claim,task.getCreateTime());

        }

    }

    private void dealSaasIput(SyncTask task,Long claimId){
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);
        ClaimDetailSyncVO syncVO = new ClaimDetailSyncVO();
        syncVO.setClaimNumber(claimId);
        request.setClaimInfo(syncVO);
        if(task.getCreateTime() != null){
            request.setEventTime(task.getCreateTime().getTime());
        }
        request.setEventType(Integer.valueOf(EventType.saa通知tpa自动化初审完成.getCode()));
        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas通知tpa自动化初审完成,cremoteRs:{}", remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("saas通知tpa自动化初审完成失败:" + remoteRs.getMessage());
        }
        Map<String, Object> rsData = remoteRs.getData();
        if( rsData == null){
            throw new RuntimeException("saa通知tpa自动化初审完成,tpa返回数据为空");
        }
        String autoDealer = (String) rsData.get("autoDealer");
        if (autoDealer == null) {
            autoDealer = "0";
        }
        Claim upDto = new Claim();
        upDto.setId(claimId);
        if ("0".equals(autoDealer)) {

            upDto.setStatus(ClaimStatusEnum.WAITING_PRE_ADUIT.getCode());
            upDto.setStatusSub(ClaimStatusEnum.WAITING_PRE_ADUIT.getSubStatus());
            upDto.setStage(ClaimStatusEnum.WAITING_PRE_ADUIT.getStage().getCode());
            upDto.setOperatorUserName("");
            upDto.setOperatorUserId("");
            upDto.setOperatorOrgId("");
            upDto.setOperatorOrgName("");
            claimService.updateClaim(upDto, false, "dealSaasIput自动化录入");
        } else {
            String operatorId = (String) rsData.get("operatorId");
            String operatorName = (String) rsData.get("operatorName");
            String operatorGroupId = (String) rsData.get("operatorGroupId");
            String operatorGroupName = (String) rsData.get("operatorGroupName");
            if (StringUtils.isBlank(operatorId)) {
                throw new RuntimeException("saas通知tpa自动化初审完成失败,未分配操作人id");
            }
            if (StringUtils.isBlank(operatorName)) {
                throw new RuntimeException("saas通知tpa自动化初审完成失败,未分配操作人name");
            }
            if (StringUtils.isBlank(operatorGroupId)) {
                throw new RuntimeException("saas通知tpa自动化初审完成失败,未分配操作人operatorGroupId");
            }
            if (StringUtils.isBlank(operatorGroupName)) {
                throw new RuntimeException("saas通知tpa自动化初审完成失败,未分配操作人operatorGroupName");
            }
            upDto.setStatus(ClaimStatusEnum.PRE_ADUITING.getCode());
            upDto.setStatusSub(ClaimStatusEnum.PRE_ADUITING.getSubStatus());
            upDto.setStage(ClaimStatusEnum.PRE_ADUITING.getStage().getCode());
            upDto.setOperatorUserName(operatorName);
            upDto.setOperatorUserId(operatorId);
            upDto.setOperatorOrgId(operatorGroupId);
            upDto.setOperatorOrgName(operatorGroupName);
            claimService.updateClaim(upDto, false, "dealSaasIput分配操作人");
        }
    }
}
