package com.bone.tpa.claim.flow.jump;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.flow.FlowFireService;
import com.bone.tpa.claim.flow.trigger.ReviewLandTrigger;
import com.bone.tpa.facade.request.*;
import com.bone.tpa.facade.vo.UserCheckVO;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import com.bone.tpa.sdk.vo.BaseFlowNodeVO;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.util.PkJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ApproveReviewFlowFireService  extends BaseFlowFireService implements FlowFireService {
    @Autowired
    private ClaimService claimService;
    @Autowired
    private ClaimTrackLogService trackLogService;
    /**
     * 向下推送流程
     *
     * @param claim
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void fire(Claim claim) {
        /*claimService.notifyTpaChangeStatus(claim,new Date(),ClaimStatusEnum.COMPLETE_AUDIT,
                    BizContextUtils.getUser(),
                    "审核完成","审核完成" );
*/
        if(!canJoin(claim)){
            throw new RuntimeException("not support fuhe stage");
        }
        if(!canFenpeiReviewPepole(claim)){
            throw new TpaBizException("赔案无法分配复核人员");
        }
        claimService.setStatusField(claim, ClaimStatusEnum.AuditingReviewIng);
        claimService.clearOperator(claim);
        claimService.updateClaim(claim, false, "ApproveReviewFlowFireService.fire");

        SpringContextUtils.getBean(ReviewLandTrigger.class).addJobAndTryFire(
                PkJsonUtil.buildPkJson("claimNumber",claim.getId().toString()),
                5,3);


    }

    /**
     * 是否能跳过这个节点
     *
     * @param claim
     * @return
     */
    @Override
    public Boolean canJoin(Claim claim) {


        ClaimFlowConfigVO flowConfigVO =  flowConfigBiz.convertById(claim.getFlowConfigId());
        BaseFlowNodeVO approveCheckFlowNode =  flowConfigVO.getFlowConfigVO().getApproveCheckFlowNode();
        boolean canJoin =   canJoinNode(claim,"复核",approveCheckFlowNode);
        return canJoin;
    }


    public boolean canFenpeiReviewPepole(Claim claim){
        //获取是否能找到复核人
        QueryUserGroupListRequest groupListRequest = new QueryUserGroupListRequest();
        //阶段:PRE_EXAM(初审)、SUBMITTING(录入)、INSPECTION(质检)、AUDITING(审核)、REVIEWING(复核)  传大写的英文
        groupListRequest.setStage(ClaimStageEnum.REVIEWING.getCode());
        groupListRequest.setClaimNumber(claim.getId());
        ApiResult<List<Map<String,Object>>> checkRemoteRs =   tpaDataSyncFeign.queryUserGroupList(groupListRequest);

        if(!checkRemoteRs.isSuccess()){
            throw new RuntimeException("预取复核人员失败");
        }
        if(PkListUtil.isEmpty(checkRemoteRs.getData())){
            commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                    "获取复核人员为空，因此不能进入复核");
            return false;
        }

        return true;

    }

    /**
     * 落地流程后的动作（一般记录一个异步任务)
     *
     * @param claim
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void doLandEvent(Claim claim) {
        //驳回记录，因为有个逻辑，谁打回的谁审核
        ClaimTrackLog trackLog =  trackLogService.queryLatestClaimRecord(claim.getId(), OperationTypeEnum.REJECT);
        if( trackLog != null){
            String extraStore =  trackLog.getExtraStore();
            if(org.apache.commons.lang.StringUtils.isBlank(extraStore)){
                throw new TpaBizException("无法定位操作人人信息");
            }
            JSONObject userInfo =     JSONObject.parseObject(extraStore);
            String userName = userInfo.getString("operName");
            UserCheckRequest checkRequest = new UserCheckRequest();
            checkRequest.setUserName(userName);
            checkRequest.setStage(ClaimStageEnum.REVIEWING.getCode());
            ApiResult<UserCheckVO>  userCheckVOApiResult =  tpaDataSyncFeign.queryUserGroupInfo(checkRequest);
            if(!userCheckVOApiResult.isSuccess()){
                throw new TpaBizException("无法定位操作人人信息");
            }
            if(userCheckVOApiResult.getData() == null){
                //因为考虑到人员离职的情况，所以这里需要做一下兼容
                trackLog = null;
            }

        }
        ClaimStatusEnum toClaimStatus = ClaimStatusEnum.AuditingReviewIng;

        //  走初审人工分配
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claim.getId());

        request.setEventTime(System.currentTimeMillis());
        request.setClaimInfo(toTpaChangeService.toSyncVo(claim.getId()));

        request.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        eventOtherRequest.setAssignStage(toClaimStatus.getStage().getCode());
        eventOtherRequest.setClaimStatus(Integer.valueOf(toClaimStatus.getCode()));
        eventOtherRequest.setAssignTag(true);
        if( trackLog == null){
            eventOtherRequest.setAssignStrategy("1");

        }else{
            eventOtherRequest.setAssignStrategy("2");
            String extraStore =  trackLog.getExtraStore();
            if(org.apache.commons.lang.StringUtils.isBlank(extraStore)){
                throw new TpaBizException("无法定位操作人人信息");
            }
            JSONObject userInfo =     JSONObject.parseObject(extraStore);
            /**
             *    rs.put("operName",operName);
             *         rs.put("operId",operId);
             *         rs.put("operOrgName",operOrgName);
             *         rs.put("operOrgId",operOrgId);
             */
            eventOtherRequest.setAssignOperatorName(userInfo.getString("operName"));
            eventOtherRequest.setAssignOperatorId(userInfo.getString("operId"));
            eventOtherRequest.setAssignOperatorGroupName(userInfo.getString("operOrgName"));
            eventOtherRequest.setAssignOperatorGroupId(userInfo.getString("operOrgId"));
        }

        TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
        tpaAddLogRequest.setClaimNumber( claim.getId());
        tpaAddLogRequest.setOperation("进入复核阶段");
        tpaAddLogRequest.setRemark("");
        tpaAddLogRequest.setCreateBy("System");
        tpaAddLogRequest.setCreateTime( (new Date()).getTime());
        eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);



        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claim.getId(),
                CommonLogType.TO_TPA_LOG, "saas通知tpa进入复核,cremoteRs:{}", remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("saas通知tpa进入复核阶段，通知tpa失败:" + remoteRs.getMessage());
        }
        claimService.clearOperator(claim);
        claimService.setStatusField(claim, toClaimStatus);
        List<String> clearFieldList = new ArrayList<>();
        //自动分配
        Map<String,Object> rsData =    remoteRs.getData();
        // waibao 外包录入  pukang 普康录入
        String operatorSource = (String)rsData.get("operatorSource");
        String operatorId = (String)rsData.get("operatorId");
        String operatorName = (String)rsData.get("operatorName");
        String operatorGroupId = (String)rsData.get("operatorGroupId");
        String operatorGroupName = (String)rsData.get("operatorGroupName");
        if (trackLog != null) {
            String extraStore =  trackLog.getExtraStore();
            if(org.apache.commons.lang.StringUtils.isBlank(extraStore)){
                throw new TpaBizException("无法定位操作人人信息");
            }
            JSONObject userInfo =     JSONObject.parseObject(extraStore);
            /**
             *    rs.put("operName",operName);
             *         rs.put("operId",operId);
             *         rs.put("operOrgName",operOrgName);
             *         rs.put("operOrgId",operOrgId);
             */
            operatorName = userInfo.getString("operName");
            operatorId = userInfo.getString("operId");
            operatorGroupId = userInfo.getString("operOrgName");
            operatorGroupName = userInfo.getString("operOrgId");
        }

        if(StringUtils.isBlank(operatorId)){
            throw new RuntimeException("复核自动分配结果为空");
        }
//        if(StringUtils.isBlank(operatorSource)){
//            throw new RuntimeException("operatorSource结果为空");
//        }

        claimService.setStatusField(claim, toClaimStatus);
        claim.setOperatorUserName(operatorName);
        claim.setOperatorUserId(operatorId);
        claim.setOperatorOrgId(operatorGroupId);
        claim.setOperatorOrgName(operatorGroupName);
//        claim.setReviewingOperatorName(operatorName);
        claimService.updateClaim(claim, false, "ApproveReviewFlowFireService.doLandEvent");
        //claimRepository.updateAndClearFields(claim, clearFieldList);

        trackLogService.addActionRecord(claim,"system",null,
                OperationTypeEnum.REVIEW_CHECK_ASSIGN,
                "分配复核操作人:"+operatorName);

        claimService.checkSameInvoiceAsync(claim.getId());

    }
}
