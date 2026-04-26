package com.bone.tpa.claim.flow.stage;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.JsonUtil;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.flow.jump.ApproveFlowFireService;
import com.bone.tpa.claim.flow.trigger.QualityCompleteTrigger;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.EventOtherRequest;
import com.bone.tpa.facade.request.TpaAddLogRequest;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.sdk.vo.QualityConfigVO;
import com.bone.tpa.util.PkJsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
public class QualityStageService extends BaseStageService{
    @Autowired
    private ClaimFlowConfigBiz claimFlowConfigBiz;
    @Autowired
    private ClaimToTpaChangeService toTpaChangeService;
    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    private ClaimTrackLogService trackLogService;
    @Autowired
    private CommonLogService commonLogService;
    @Autowired
    private ApproveFlowFireService approveFlowFireService;
    /**
     * 页面执行同意
     * @param claimNumber
     */
    @Transactional(rollbackFor = Exception.class)
    public void pageApproveAction(Long claimNumber){
        // TODO


        Claim  claim =  claimRepository.findById(claimNumber);
        ClaimFlowConfigVO configVO =   claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        Map<String,String>  validMap =  checkQualityPageComplete(claimNumber,configVO.getQualityConfigVO());
        if(!validMap.isEmpty()){
            //校验失败
            throw new TpaBizException(errorMapToString(validMap));
        }

        claim.setId(claimNumber);
        Map<String,Object> traceLogExtraStore = claimService.storeOperatorInfoJson(claim);
        claimService.setStatusField(claim,ClaimStatusEnum.COMPLETE_INSPECTION);
        claimService.clearOperator(claim);
        claim.setInspectionPassTime(new Date());
        claimService.updateClaim(claim, false, "QualityStageService质检完成");

        trackLogService.addActionRecord(claim, BizContextUtils.getUser(), traceLogExtraStore,
                OperationTypeEnum.QUALITY_CHECK_COMPLETE,"质检完成");
        Integer cfgBizType =  claim.getCfgBizType();
        if( cfgBizType == 2){
            //通知tpa质检完成,走半流程的模式
            TpaSubmitClaimResult request = new TpaSubmitClaimResult();
            request.setClaimNumber(claimNumber);
            request.setClaimInfo(toTpaChangeService.toSyncVo(claimNumber));
            request.setEventType(Integer.valueOf(EventType.saas通知tpa质检完成.getCode()));
            ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
            commonLogService.addClaimLogAsync(claimNumber, CommonLogType.TO_TPA_LOG, "saas通知tpa质检完成,request:{},remoteRs:{}", request, JSONObject.toJSONString(remoteRs));
            if (!remoteRs.isSuccess()) {
                log.error("saas通知tpa质检完成失败,claimId:{},remoteRs:{}", claimNumber, JSONObject.toJSONString(remoteRs));
                throw new RuntimeException("saas通知tpa质检失败:" + JSONObject.toJSONString(remoteRs));
            }

        }else{
            claimService.notifyTpaChangeStatus(claim,new Date(),ClaimStatusEnum.COMPLETE_INSPECTION, PkListUtil.newArrayList(),
                    BizContextUtils.getUser(),
                    "质检完成","质检完成" ,false);

            SpringContextUtils.getBean(QualityCompleteTrigger.class).
                    addJobAndTryFire(
                            PkJsonUtil.buildPkJson("claimNumber",claimNumber.toString(),
                                    "operator", BizContextUtils.getUser()
                            ),3,3);
        }


    }

    public Map<String,String> checkQualityPageComplete(Long claimNumber, QualityConfigVO configVO){
        Map<String,String> rs= new HashMap<>();

        if("1".equals(configVO.getInvoiceImageBind())){
            rs.putAll(checkInvoiceBindImage(claimNumber));
        }
        if("1".equals(configVO.getInvoiceDeepType())){
            rs.putAll(checkInvoiceDeepType(claimNumber));
        }

        return  rs;
    }
    /**
     * 推进流程
     * @param claimNumber
     * @param operator
     */
    @Transactional(rollbackFor = Exception.class)
    public void qualityCompleteTriggerAction(Long claimNumber,String operator,
                                             Date eventTime){
        Claim  claim =  claimRepository.findById(claimNumber);

        claimService.notifyTpaChangeStatus(claim,eventTime,ClaimStatusEnum.COMPLETE_INSPECTION,PkListUtil.newArrayList(),
                operator,null,"人工质检完成",true );
        commonLogService.addLogASync(claimNumber.toString(), CommonLogType.FLOW_LOG,
                "人工质检完成，尝试推进审核环节");

        //推进下一步
        approveFlowFireService.fire(claim);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimStatusEnum inputDealerApply(Claim claim, Date eventTime){
        Long claimId = claim.getId();
        ClaimFlowConfigVO flowConfigVO =  claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        //初审配置节点
        QualityConfigVO configVO = flowConfigVO.getQualityConfigVO();
        /**
         * 处理人分配策略
         * 0 手工分配
         * 1 随机分配
         */
        String assignType = configVO.getDealerAssignType();
        ClaimStatusEnum toClaimStatus = ClaimStatusEnum.WAITING_INSPECTION;
        ClaimTrackLog oldLog = trackLogService.queryLatestClaimRecordByStage(claimId, ClaimStageEnum.INSPECTION,
                OperationTypeEnum.QUALITY_CHECK_COMPLETE, OperationTypeEnum.BACK_NODE);
        if (oldLog != null) {
            //这里仅是搞成31
            toClaimStatus = ClaimStatusEnum.INSPECTIONING;
        } else if(StringUtils.equals("1",assignType)){
            toClaimStatus = ClaimStatusEnum.INSPECTIONING;
        }
        //  走初审人工分配
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);

        request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));

        request.setEventTime(eventTime.getTime());

        request.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        eventOtherRequest.setAssignStage(ClaimStageEnum.INSPECTION.getCode());
        eventOtherRequest.setClaimStatus(Integer.valueOf(toClaimStatus.getCode()));
        eventOtherRequest.setAssignTag(true);
        eventOtherRequest.setAssignStrategy(assignType);
        if (oldLog != null) {
            //手动分配了
            eventOtherRequest.setAssignStrategy("2");
        }

        if(toClaimStatus == ClaimStatusEnum.WAITING_INSPECTION){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入质检等待分配");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        if(toClaimStatus == ClaimStatusEnum.INSPECTIONING){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入质检阶段");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas通知tpa进入质检阶段,cremoteRs:{}", remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("saas通知tpa进入质检阶段，通知tpa失败:" + remoteRs.getMessage());
        }
        claim.setId(claimId);
        claim.setBizIdentityCode(claim.getBizIdentityCode());
        claimService.clearOperator(claim);
        claimService.setStatusField(claim, toClaimStatus);
        List<String> clearFieldList = new ArrayList<>();
        if(toClaimStatus ==ClaimStatusEnum.INSPECTIONING ){
            //自动分配
            Map<String,Object> rsData =    remoteRs.getData();
            // waibao 外包录入  pukang 普康录入
            String operatorSource = (String)rsData.get("operatorSource");
            String operatorId = (String)rsData.get("operatorId");
            String operatorName = (String)rsData.get("operatorName");
            String operatorGroupId = (String)rsData.get("operatorGroupId");
            String operatorGroupName = (String)rsData.get("operatorGroupName");


            if (oldLog != null) {
                String extraStore =  oldLog.getExtraStore();
                if(org.apache.commons.lang.StringUtils.isBlank(extraStore)){
                    throw new TpaBizException("无法定位操作人人信息");
                }
                JSONObject userInfo =     JSONObject.parseObject(extraStore);

                operatorId = userInfo.getString("operId");
                operatorName = userInfo.getString("operName");
                operatorGroupId = userInfo.getString("operOrgId");
                operatorGroupName = userInfo.getString("operOrgName");

                if(StringUtils.isBlank(operatorName)){
                    throw new RuntimeException("该赔案退回之前没有质检人员");
                }
            }

            if(StringUtils.isBlank(operatorId)){
                throw new RuntimeException("质检自动分配结果为空");
            }
//            if(StringUtils.isBlank(operatorSource)){
//                throw new RuntimeException("operatorSource结果为空");
//            }

            claimService.setStatusField(claim, toClaimStatus);
            claim.setOperatorUserName(operatorName);
            claim.setOperatorUserId(operatorId);
            claim.setOperatorOrgId(operatorGroupId);
            claim.setOperatorOrgName(operatorGroupName);
//            claim.setInspectionOperatorName(operatorName);
            claimService.updateClaim(claim, false, "QualityStageService自动分配");

            //claimRepository.updateAndClearFields(claim,clearFieldList);

            trackLogService.addActionRecord(claim,"system",null, OperationTypeEnum.QUALITY_CHECK_ASSIGN,
                    "自动分配质检操作人:"+operatorName);

        } else{
            //手工指定
            claimService.updateClaim(claim, false, "QualityStageService手工分配");
        }

        return toClaimStatus;
    }
}
