package com.bone.tpa.claim.flow.stage;

import cn.hutool.core.date.DateUtil;
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
import com.bone.tpa.claim.flow.jump.ApproveReviewFlowFireService;
import com.bone.tpa.claim.flow.trigger.ApproveToEndTrigger;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.EventOtherRequest;
import com.bone.tpa.facade.request.ReportLogRequest;
import com.bone.tpa.facade.request.TpaAddLogRequest;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.push.service.PushClaimService;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.ApproveConfigVO;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.util.PkJsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ApproveStageService extends BaseStageService{
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
    private ApproveReviewFlowFireService reviewFlowFireService;

    @Autowired
    private PushClaimService pushClaimService;

    /**
     * 页面审核
     * @param claimNumber
     */
    @Transactional(rollbackFor = Exception.class)
    public void pageApproveAction(Long claimNumber){
        // TODO
        String operator = BizContextUtils.getUser();
        if(StringUtils.isBlank(operator)){
            operator = "System";
        }
        Claim  claim =  claimRepository.findById(claimNumber);
        ClaimFlowConfigVO configVO =   claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        Map<String,String>  validMap =  checkApprovePageComplete(claimNumber,configVO.getApproveConfigVO());
        if(!validMap.isEmpty()){
            //校验失败
            throw new TpaBizException(errorMapToString(validMap));
        }
        Boolean canJoinReviewing = reviewFlowFireService.canJoin(claim);
        Map<String,Object> traceLogExtraStore = claimService.storeOperatorInfoJson(claim);
        claimService.clearOperator(claim);
        claim.setInspectionPassTime(new Date());
        trackLogService.addActionRecord(claim, operator, traceLogExtraStore,
                OperationTypeEnum.APPROVE_MANUAL_COMPLETE,"审核完成");
        if(canJoinReviewing){
            if(!reviewFlowFireService.canFenpeiReviewPepole(claim)){
                throw new TpaBizException("赔案无法分配复核人员");
            }
            claimService.updateClaim(claim, false, "pageApproveAction进入复核");

            //进入复核
            //完成审核，tpa需要记录reportlog，并且更新状态
            //完成审核
            String dateFormate = "yyyy-MM-dd HH:mm:ss";
            ReportLogRequest reportLogRequest = new ReportLogRequest();
            reportLogRequest.setStage( ClaimStatusEnum.Auditing.getStage().getCode());
            reportLogRequest.setOperatorName(operator);
            reportLogRequest.setStatus("已审核");
            reportLogRequest.setEndTime(DateUtil.format(new Date(),dateFormate));

            ReportLogRequest reviewLogLogRequest = new ReportLogRequest();
            reviewLogLogRequest.setStage( ClaimStatusEnum.AuditingReviewIng.getStage().getCode());
            reviewLogLogRequest.setOperatorName(operator);
            reviewLogLogRequest.setStatus("复核中");
            reviewLogLogRequest.setEndTime("");
            reviewLogLogRequest.setStartTime(DateUtil.format(new Date(),dateFormate));




            reviewFlowFireService.fire(claim);
            //直接到复核中
            claimService.notifyTpaChangeStatus(claim,new Date(),ClaimStatusEnum.AuditingReviewIng,
                    PkListUtil.asList(reportLogRequest,reviewLogLogRequest),
                    operator,
                    "审核完成进入复核","审核完成进入复核" ,false);

        }else{

            //直接审核完成
            claimService.setStatusField(claim,ClaimStatusEnum.COMPLETE_AUDIT);
            claim.setPkPushStatus(PkPushStatusEnum.WAITING_PUSH.getName());
            claim.setInsurancePushStatus(InsurancePushStatusEnum.WAITING_PUSH.getName());
            claimService.updateClaim(claim, false, "pageApproveAction直接审核完成");
            //完成审核
            claimService.notifyTpaChangeStatus(claim,new Date(),ClaimStatusEnum.COMPLETE_AUDIT,
                    PkListUtil.newArrayList(),
                    BizContextUtils.getUser(),
                    "审核完成到终态","审核完成到终态" ,true);

            pushClaimService.push(claim.getId(), null);

            SpringContextUtils.getBean(ApproveToEndTrigger.class)
                    .addJobAndTryFire(
                            PkJsonUtil.buildPkJson(
                                    "claimNumber",claim.getId().toString(),
                                    "operator", BizContextUtils.getUser()
                            ),3,3
                    );
        }
    }

    public Map<String,String> checkApprovePageComplete(Long claimNumber, ApproveConfigVO configVO){
        Map<String,String> rs= new HashMap<>();


        if("1".equals(configVO.getInvoiceDeepType())){
            rs.putAll(checkInvoiceDeepType(claimNumber));
        }

        return  rs;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimStatusEnum inputDealerApply(Claim claim, Date eventTime){
        Long claimId = claim.getId();
        ClaimFlowConfigVO flowConfigVO =  claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        //初审配置节点
        ApproveConfigVO approveConfigVO = flowConfigVO.getApproveConfigVO();
        /**
         * 处理人分配策略
         * 0 手工分配
         * 1 随机分配
         */
        String assignType = approveConfigVO.getDealerAssignType();
        ClaimStatusEnum toClaimStatus = ClaimStatusEnum.WaitAudit;
        ClaimTrackLog oldLog = trackLogService.queryLatestClaimRecordByStage(claimId, ClaimStageEnum.AUDITING,
                OperationTypeEnum.APPROVE_MANUAL_COMPLETE, OperationTypeEnum.BACK_NODE);
        if (oldLog != null) {
            //这里仅是搞成41
            toClaimStatus = ClaimStatusEnum.Auditing;
        } else if(StringUtils.equals("1",assignType)){
            toClaimStatus = ClaimStatusEnum.Auditing;
        }
        //  走初审人工分配
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);

        request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));

        request.setEventTime(eventTime.getTime());

        request.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        eventOtherRequest.setAssignStage(ClaimStageEnum.AUDITING.getCode());
        eventOtherRequest.setClaimStatus(Integer.valueOf(toClaimStatus.getCode()));
        eventOtherRequest.setAssignTag(true);
        eventOtherRequest.setAssignStrategy(assignType);
        if (oldLog != null) {
            //手动分配了
            eventOtherRequest.setAssignStrategy("2");
        }


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

        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas通知tpa进入审核阶段,cremoteRs:{}", remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("saas通知tpa进入审核阶段，通知tpa失败:" + remoteRs.getMessage());
        }
        Claim updto = new Claim();
        updto.setId(claimId);
        updto.setBizIdentityCode(claim.getBizIdentityCode());
        claimService.clearOperator(updto);
        claimService.setStatusField(updto, toClaimStatus);
        List<String> clearFieldList = new ArrayList<>();
        if(toClaimStatus ==ClaimStatusEnum.Auditing ){
            //自动分配
            Map<String,Object> rsData =    remoteRs.getData();
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
                    throw new TpaBizException("该赔案退回之前没有审核人员，请联系工作人员");
                }
            }

            if(StringUtils.isBlank(operatorId)){
                throw new RuntimeException("审核自动分配结果为空");
            }
//            if(StringUtils.isBlank(operatorSource)){
//                throw new RuntimeException("operatorSource结果为空");
//            }

            claimService.setStatusField(updto, toClaimStatus);
            updto.setOperatorUserName(operatorName);
            updto.setOperatorUserId(operatorId);
            updto.setOperatorOrgId(operatorGroupId);
            updto.setOperatorOrgName(operatorGroupName);
//            updto.setAuditingOperatorName(operatorName);
            claimService.updateClaim(updto, false, "ApproveStageService自动分配");

            //claimRepository.updateAndClearFields(updto,clearFieldList);

            trackLogService.addActionRecord(claim,"system",null, OperationTypeEnum.QUALITY_CHECK_ASSIGN,
                    "自动分配审核操作人:"+operatorName);

        } else{
            //手工指定
            claimService.updateClaim(updto, false, "ApproveStageService手工分配");
        }

        return toClaimStatus;
    }

}
