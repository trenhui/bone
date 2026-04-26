package com.bone.tpa.soa.syncevent.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.request.SyncClaimWithEventRequest;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.flow.jump.PrecheckFlowFireService;
import com.bone.tpa.claim.flow.trigger.PrecheckStartTrigger;
import com.bone.tpa.sdk.claim.enums.CfgAutoTypeEnum;
import com.bone.tpa.sdk.claim.enums.ClaimFlowStatus;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimFlowConfig;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.soa.syncevent.BaseEventAction;
import com.bone.tpa.soa.syncevent.SyncEvnetAction;
import com.bone.tpa.task.impl.AutoPreExameTrigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class TpaSignClaimToSaas extends BaseEventAction implements SyncEvnetAction {

    @Autowired
    private PrecheckFlowFireService precheckFlowFireService;
    @Autowired
    ClaimTrackLogService trackLogService;
    @Autowired
    private ClaimFlowConfigBiz claimFlowConfigBiz;

    @Override
    public EventType getEvent() {
        return EventType.tpa签收赔案下发到saas初审;
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> fire(SyncClaimWithEventRequest request) {
        Map<String, Object>  rs = new HashMap<>();

        //存储数据
        Long claimNumber =  request.getClaimNumber();
        if( claimNumber == null){
            throw new IllegalArgumentException("claimNumber is null");
        }
        commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                "tpa签收赔案下发到saas初审,claimNumber:{},data:{}", request.getClaimNumber(),
                JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Claim checkExist =  claimRepository.findById(claimNumber);
        if( checkExist != null){
            throw new IllegalArgumentException("claim is aleady exist");
        }
       //保存数据
        syncFromTpaService.transfer(request);
        commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                "tpa签收赔案下发到saas初审,claimNumber:{},保存数据结束", request.getClaimNumber());
        checkExist =  claimRepository.findById(claimNumber);


        Claim upDto = new Claim();
        upDto.setId(claimNumber);
        upDto.setBizIdentityCode(checkExist.getBizIdentityCode());
        //清空操作人
        clearOperator(upDto);
        upDto.setSignPassTime(new Date());

        ClaimFlowConfig activeConfig =  claimFlowConfigBiz.getByIdentityCodeAndStatus(checkExist.getBizIdentityCode(),
                ClaimFlowStatus.Active);
        if( activeConfig != null){
            upDto.setFlowConfigId(activeConfig.getId());
        }
        claimService.setStatusField(upDto, ClaimStatusEnum.SIGNED);
        claimService.updateClaim(upDto, false, "TpaSignClaimToSaas.fire");
        checkExist =  claimRepository.findById(claimNumber);


        trackLogService.addActionRecord(checkExist,"system",null,
                OperationTypeEnum.SIGN,"签收成功");

        //先签收进来，再异步任务推进流程
        SpringContextUtils.getBean(PrecheckStartTrigger.class).addJobAndTryFire(claimNumber.toString(),
                3,2);
        rs.put(BIZ_IDENTITY_CODE, checkExist.getBizIdentityCode());
        rs.put(TENANT_ID,checkExist.getTenantId());
        /*
        rs.put(BIZ_IDENTITY_CODE, checkExist.getBizIdentityCode());
        rs.put(TENANT_ID,checkExist.getTenantId());
         if (cfgAutoType == CfgAutoTypeEnum.AUTO && preCheckOpen){
            //自动化作业
             commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                     "tpa签收赔案下发到saas初审,claimNumber:{},进入自动化作业", request.getClaimNumber());
            upDto.setStatus(ClaimStatusEnum.ROBOT_PRE_ADUIT.getCode());
            upDto.setStatusSub(ClaimStatusEnum.ROBOT_PRE_ADUIT.getSubStatus());
             upDto.setStage(ClaimStatusEnum.ROBOT_PRE_ADUIT.getStage().getCode());
            rs.put(CLAIM_STATUS, ClaimStatusEnum.ROBOT_PRE_ADUIT.getCode());
            rs.put(CLAIM_STATUS_DESC, ClaimStatusEnum.ROBOT_PRE_ADUIT.getValue());

            //记录一个异步任务
             //记录一个自动化初审的任务，3秒后线程池执行
             autoPreExameTrigger.addJobAndTryFire(claimNumber.toString(),
                     1,3
             );


        }else{
            //非自动化作业
            //等待tpa分配
             commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                     "tpa签收赔案下发到saas初审,claimNumber:{},进入非自动化作业", request.getClaimNumber());
            upDto.setStatus(ClaimStatusEnum.WAITING_PRE_ADUIT.getCode());
             upDto.setStage(ClaimStatusEnum.WAITING_PRE_ADUIT.getStage().getCode());
             upDto.setStatusSub(ClaimStatusEnum.WAITING_PRE_ADUIT.getSubStatus());
            rs.put(CLAIM_STATUS, ClaimStatusEnum.WAITING_PRE_ADUIT.getCode());
            rs.put(CLAIM_STATUS_DESC, ClaimStatusEnum.WAITING_PRE_ADUIT.getValue());

        }
        claimRepository.update(upDto);
        commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                "tpa签收赔案下发到saas初审,claimNumber:{},返回:{}", request.getClaimNumber(),
                JSONObject.toJSONString(rs, SerializerFeature.DisableCircularReferenceDetect));*/
        return rs;
    }
}
