package com.bone.tpa.claim.flow.stage;

import com.bone.core.util.BizContextUtils;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimImageService;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.flow.jump.InputFlowFireService;
import com.bone.tpa.claim.flow.trigger.PrecheckCompleteTrigger;
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
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.sdk.vo.PreCheckConfigVO;
import com.bone.tpa.util.PkJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PreCheckStageService extends BaseStageService{
    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;

    @Autowired
    private  ClaimToTpaChangeService toTpaChangeService;

    @Autowired
    private CommonLogService commonLogService;
    @Autowired
    private InputFlowFireService inputFlowFireService;

    @Autowired
    private ClaimImageService claimImageService;


    @Autowired
    private ClaimTrackLogService trackLogService;
    @Autowired
    private ClaimFlowConfigBiz claimFlowConfigBiz;



    @Transactional(rollbackFor = Exception.class)
    public Map<String,String> preCheckPassValid(Claim claim, Date eventTime){
        Map<String,String> rs = new HashMap<>();
        ClaimFlowConfigVO flowConfigVO =  claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        //初审配置节点
        PreCheckConfigVO preCheckConfigVO = flowConfigVO.getPreCheckConfigVO();
        /**
         * 影像分类规则
         * 0 有影像件分类即可(分类的影像件>=1)
         * 1 所有影像件均分类
         * 2 无需强制分类
         */
        String categoryRule =  preCheckConfigVO.getCategoryRule();
        List<ClaimImage>  imageList =  claimImageService.getListByClaimNumber(claim.getId());
        if("0".equals(categoryRule)){
           if(PkListUtil.isNotEmpty(imageList)){
               boolean ret = false;

               for(ClaimImage image:imageList){
                   if(StringUtils.isNotBlank(image.getImageType() )){
                       ret = true;
                       break;
                   }
               }
               if(!ret){
                   rs.put("categoryRule","校验规则：有影像件分类即可(分类的影像件>=1) 失败");
               }
           }
        }
        if("1".equals(categoryRule)){
            if(PkListUtil.isNotEmpty(imageList)){
                boolean ret = true;

                for(ClaimImage image:imageList){
                    if(StringUtils.isBlank(image.getImageType() )){
                        ret = false;
                        break;
                    }
                }
                if(!ret){
                    rs.put("categoryRule","校验规则：所有影像件均分类 失败");
                }
            }
        }

        return rs;
    }

    /**
     * 页面初审完成
     * @param claim
     */
    @Transactional(rollbackFor = Exception.class)
    public void pageSubmit(Claim claim){
        //校验数据正确性
        Map<String,String> checkRs =   preCheckPassValid(claim,new Date());
        if(!checkRs.isEmpty()){
            throw new TpaBizException(errorMapToString(checkRs));
        }
        Map<String,Object> extraTrackLogStore =   claimService.storeOperatorInfoJson(claim);;
        claimService.setStatusField(claim,ClaimStatusEnum.COMPLETE_PRE_ADUIT);
        claim.setPreExamPassTime(new Date());
        claim.setPreExamOperatorName(claim.getOperatorUserName());
        claimService.clearOperator(claim);
        claimService.updateClaim(claim, false, "pageSubmit");

        trackLogService.addActionRecord(claim,BizContextUtils.getUser(),extraTrackLogStore,
                OperationTypeEnum.PRE_CHECK_MANUAL_COMPLETE ,
                  "初审通过");

        //通知tpa初审通过
        claimService.notifyTpaChangeStatus(claim ,new Date(), ClaimStatusEnum.COMPLETE_PRE_ADUIT
                ,PkListUtil.newArrayList(),
                BizContextUtils.getUser(),"初审完成", "初审通过",false);
        //推进流程
        SpringContextUtils.getBean(PrecheckCompleteTrigger.class).addJobAndTryFire(
                PkJsonUtil.buildPkJson(
                        "claimNumber",claim.getId().toString(),
                        "operator", BizContextUtils.getUser()
                ),3,3
        );
    }


    /**
     * 初审设置处理人（也有可能不设置
     * @param claim
     */
    @Transactional(rollbackFor = Exception.class)
    public ClaimStatusEnum preCheckDealerApply(Claim claim, Date eventTime){
        Long claimId = claim.getId();
        ClaimFlowConfigVO flowConfigVO =  claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        //初审配置节点
        PreCheckConfigVO preCheckConfigVO = flowConfigVO.getPreCheckConfigVO();
        /**
         * 处理人分配策略
         * 0 手工分配
         * 1 随机分配
         */
        String assignType = preCheckConfigVO.getDealerAssignType();
        ClaimStatusEnum toClaimStatus = ClaimStatusEnum.WAITING_PRE_ADUIT;
        if(StringUtils.equals("1",assignType)){
            toClaimStatus = ClaimStatusEnum.PRE_ADUITING;
        }
        //  走初审人工分配
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);
        ClaimDetailSyncVO syncVO = new ClaimDetailSyncVO();
        syncVO.setClaimNumber(claimId);
        request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));
        request.setEventTime(eventTime.getTime());
        request.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        eventOtherRequest.setAssignStage(ClaimStageEnum.PRE_EXAM.getCode());
        eventOtherRequest.setClaimStatus(Integer.valueOf(toClaimStatus.getCode()));
        eventOtherRequest.setAssignTag(true);
        eventOtherRequest.setAssignStrategy(assignType);


        if(toClaimStatus == ClaimStatusEnum.WAITING_PRE_ADUIT){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入初审等待分配");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        if(toClaimStatus == ClaimStatusEnum.PRE_ADUITING){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入人工初审");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas初审申请tpa分配人员,request:{}", request);

        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas初审申请tpa分配人员,cremoteRs:{}", remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("初审申请分配人员，通知tpa失败:" + remoteRs.getMessage());
        }
        Claim updto = new Claim();
        updto.setId(claimId);
        updto.setBizIdentityCode(claim.getBizIdentityCode());
        clearOperator(updto);
        claimService.setStatusField(updto, toClaimStatus);
        List<String> clearFieldList = new ArrayList<>();
        if(toClaimStatus ==ClaimStatusEnum.PRE_ADUITING ){
            //自动分配
            Map<String,Object> rsData =    remoteRs.getData();
            // waibao 外包录入  pukang 普康录入
            String operatorId = (String)rsData.get("operatorId");
            String operatorName = (String)rsData.get("operatorName");
            String operatorGroupId = (String)rsData.get("operatorGroupId");
            String operatorGroupName = (String)rsData.get("operatorGroupName");
            if(StringUtils.isBlank(operatorId)){
                throw new RuntimeException("初审自动分配结果为空");
            }
            updto.setOperatorUserName(operatorName);
            updto.setOperatorUserId(operatorId);
            updto.setOperatorOrgId(operatorGroupId);
            updto.setOperatorOrgName(operatorGroupName);
//            updto.setPreExamOperatorName(operatorName);
            claimService.updateClaim(updto, false, "PreCheckStageService自动分配");

            //claimRepository.updateAndClearFields(updto,clearFieldList);

            trackLogService.addActionRecord(claim,"system",null, OperationTypeEnum.PRE_CHECK_ASSIGN,
                    "自动分配初审操作人:"+operatorName);
        } else{
            //手工指定
            claimService.updateClaim(updto, false, "PreCheckStageService手工分配");
        }

        return toClaimStatus;
    }


    public void clearOperator(Claim upDto){
        upDto.setOperatorUserName("");
        upDto.setOperatorUserId("");
        upDto.setOperatorOrgId("");
        upDto.setOperatorOrgName("");
    }
}
