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
import com.bone.tpa.claim.flow.jump.QualityFlowFireService;
import com.bone.tpa.claim.flow.trigger.InputPersonCompleteTrigger;
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
import com.bone.tpa.sdk.vo.InputConfigVO;
import com.bone.tpa.util.PkJsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class InputStageService extends BaseStageService{

    public static final String pukang_source = "pukang";
    public static final String waibao_source = "waibao";

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    private QualityFlowFireService qualityFlowFireService;

    @Autowired
    private CommonLogService commonLogService;

    @Autowired
    private ClaimToTpaChangeService toTpaChangeService;

    @Autowired
    private ClaimTrackLogService trackLogService;
    @Autowired
    private ClaimFlowConfigBiz claimFlowConfigBiz;




    /**
     * 外包录入成功
     * 只需要更新状态，不用通知tpa
     *
     * @param claimId
     */
    public void waibaoInputSuccess(Long claimId) {
        Claim claimCheck = claimRepository.findById(claimId);

        claimCheck.setId(claimId);
        claimCheck.setBizIdentityCode(claimCheck.getBizIdentityCode());
        claimCheck.setSubmittingPassTime(new Date());
        Map<String,Object> extraTrackLogStore = claimService.storeOperatorInfoJson(claimCheck);
        claimService.clearOperator(claimCheck);
        claimService.setStatusField(claimCheck, ClaimStatusEnum.INPUT_COMPLETE);
        //状态改成已录入
        claimService.updateClaim(claimCheck, false, "waibaoInputSuccess");
        //记录一个traceLog
        trackLogService.addActionRecord(claimCheck, claimCheck.getSubmittingOperatorName(),
                extraTrackLogStore, OperationTypeEnum.WAIBAO_INPUT_COMPELTE, "外包录入完成");

        //启动异步流程去推送
        SpringContextUtils.getBean(InputPersonCompleteTrigger.class).
                addJobAndTryFire(PkJsonUtil.buildPkJson(
                        "claimNumber", claimId.toString(),
                        "operator", claimCheck.getSubmittingOperatorName()

                ), 1, 3);


    }



    /**
     * 页面录入通过
     * @param claimNumber
     */
    @Transactional(rollbackFor = Exception.class)
    public void pageApproveAction(Long claimNumber){
        Claim  claim =  claimRepository.findById(claimNumber);
        claim.setId(claimNumber);

        ClaimFlowConfigVO configVO =   claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        Map<String,String>  validMap =  checkInputPageComplete(claimNumber,configVO.getInputConfigVO());
        if(!validMap.isEmpty()){
            //校验失败
            throw new TpaBizException(errorMapToString(validMap));
        }


        Map<String,Object> traceLogExtraStore = claimService.storeOperatorInfoJson(claim);
        claimService.setStatusField(claim,ClaimStatusEnum.INPUT_COMPLETE);
        claimService.clearOperator(claim);
        claim.setSubmittingPassTime(new Date());
        claimService.updateClaim(claim, false, "pageApproveAction");
        trackLogService.addActionRecord(claim, BizContextUtils.getUser(), traceLogExtraStore,
                OperationTypeEnum.INPUT_MANUAL_COMPELTE,"人工录入完成");

        claimService.notifyTpaChangeStatus(claim,new Date(),ClaimStatusEnum.INPUT_COMPLETE, PkListUtil.newArrayList(),
                BizContextUtils.getUser(),
                "人工录入完成","人工录入完成",false );

        SpringContextUtils.getBean(InputPersonCompleteTrigger.class).
                addJobAndTryFire(
                        PkJsonUtil.buildPkJson("claimNumber",claimNumber.toString(),
                        "operator", BizContextUtils.getUser()
                        ),

                        1,3);
    }


    public Map<String,String> checkInputPageComplete(Long claimNumber,InputConfigVO inputConfigVO){
        Map<String,String> rs= new HashMap<>();

        if("1".equals(inputConfigVO.getInvoiceImageBind())){
            rs.putAll(checkInvoiceBindImage(claimNumber));
        }
        if("1".equals(inputConfigVO.getInvoiceDeepType())){
            rs.putAll(checkInvoiceDeepType(claimNumber));
        }

        return  rs;
    }

    /**
     * 人工录入完成的后续动作
     * @param claimNumber
     * @param eventTime
     */
    @Transactional(rollbackFor = Exception.class)
    public void inputPersonComplete(Long claimNumber,String operator,
                                    Date eventTime){
        Claim  claim =  claimRepository.findById(claimNumber);

        //这里就不加tpa日志了
        claimService.notifyTpaChangeStatus(claim,eventTime,ClaimStatusEnum.INPUT_COMPLETE,PkListUtil.newArrayList(),
              operator,null,"人工录入完成",true );
        commonLogService.addLogASync(claimNumber.toString(), CommonLogType.FLOW_LOG,
                "人工录入完成，尝试推进质检环节");

        //推进下一步
        qualityFlowFireService.fire(claim);
    }
    /**
     * ocr 录入后的完成动作
     * @param claimNumber
     */
    @Transactional(rollbackFor = Exception.class)
    public void inputOcrCompleteAction(Long claimNumber){


        Claim  claim =  claimRepository.findById(claimNumber);
        ClaimFlowConfigVO flowConfigVO =  claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        InputConfigVO inputConfigVO =  flowConfigVO.getInputConfigVO();
        //记录一个traceLog
        trackLogService.addActionRecord(claim,"system",null, OperationTypeEnum.INPUT_OCR,
                "自动化录入完成" );


        /**
         * /**
         *      * 0 先技力再传统录入
         *      * 1 仅传统录入
         *      * 2 仅技力录入(在质检环境补充)
         *      */
        String inputType =   inputConfigVO.getInputType();
        if("1".equals(inputType)|| "0".equals(inputType)){
            //分配人
            commonLogService.addLogASync(claimNumber.toString(), CommonLogType.FLOW_LOG,
                    "ocr录入完成，进行人员分配");
             inputDealerApply(claim ,new Date());
        }else{

            claim.setSubmittingOperatorName("system");
            claim.setSubmittingPassTime(new Date());
            claimService.setStatusField(claim,ClaimStatusEnum.INPUT_COMPLETE);
            claimService.clearOperator(claim);
            claimService.updateClaim(claim, false, "inputOcrCompleteAction");
            claimService.notifyTpaChangeStatus(claim,new Date(),ClaimStatusEnum.INPUT_COMPLETE,PkListUtil.newArrayList(),
                    "System","ocr录入完成进入质检","ocr录入完成进入质检",true );

            //直接进入质检环节
            commonLogService.addLogASync(claimNumber.toString(), CommonLogType.FLOW_LOG,
                    "ocr录入完成，尝试推进质检环节");
            qualityFlowFireService.fire(claim);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimStatusEnum inputDealerApply(Claim claim, Date eventTime){
        Long claimId = claim.getId();
        ClaimFlowConfigVO flowConfigVO =  claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        //录入配置节点
        InputConfigVO inputConfigVO = flowConfigVO.getInputConfigVO();
        /**
         * 处理人分配策略
         * 0 手工分配
         * 1 随机分配
         */
        String assignType = inputConfigVO.getDealerAssignType();
        ClaimStatusEnum toClaimStatus = ClaimStatusEnum.ADDING_INPUT;
        ClaimTrackLog oldLog = trackLogService.queryLatestClaimRecordByStage(claimId, ClaimStageEnum.SUBMITTING,
                OperationTypeEnum.INPUT_MANUAL_COMPELTE, OperationTypeEnum.WAIBAO_INPUT_COMPELTE, OperationTypeEnum.BACK_NODE);
        if (oldLog != null) {
            //这里仅是搞成22
            toClaimStatus = ClaimStatusEnum.WAIBAO_INPUTING;
        } else if(StringUtils.equals("1",assignType)){
            //随机分配
            toClaimStatus = ClaimStatusEnum.WAIBAO_INPUTING;
        }
        //  走初审人工分配
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);

        request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));

        request.setEventTime(eventTime.getTime());

        request.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        eventOtherRequest.setAssignStage(ClaimStageEnum.SUBMITTING.getCode());
        eventOtherRequest.setClaimStatus(Integer.valueOf(toClaimStatus.getCode()));
        eventOtherRequest.setAssignTag(true);
        eventOtherRequest.setAssignStrategy(assignType);
        if (oldLog != null) {
            //手动分配了
            eventOtherRequest.setAssignStrategy("2");
        }

        if(toClaimStatus == ClaimStatusEnum.ADDING_INPUT){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入录入等待分配");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        if(toClaimStatus == ClaimStatusEnum.WAIBAO_INPUTING){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入录入阶段");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas通知tpa自动化初审完成,cremoteRs:{}", remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("自动化初审完成，通知tpa失败:" + remoteRs.getMessage());
        }
        Claim updto = new Claim();
        updto.setId(claimId);
        updto.setBizIdentityCode(claim.getBizIdentityCode());
        claimService.clearOperator(updto);
        claimService.setStatusField(updto, toClaimStatus);
        List<String> clearFieldList = new ArrayList<>();
        if(toClaimStatus ==ClaimStatusEnum.WAIBAO_INPUTING ){
            //自动分配
            Map<String,Object> rsData =    remoteRs.getData();
            // waibao 外包录入  pukang 普康录入
            String operatorSource = (String)rsData.get("operatorSource");
            String operatorId = (String)rsData.get("operatorId");
            String operatorName = (String)rsData.get("operatorName");
            String operatorGroupId = (String)rsData.get("operatorGroupId");
            String operatorGroupName = (String)rsData.get("operatorGroupName");

//            if(StringUtils.isBlank(operatorSource)){
//                throw new RuntimeException("operatorSource结果为空");
//            }
            if(waibao_source.equals(operatorSource)){
                if(StringUtils.isBlank(operatorId)){
                    throw new RuntimeException("初审自动分配结果为空");
                }
                toClaimStatus= ClaimStatusEnum.WAIBAO_INPUTING;
            } else if (oldLog != null) {
                toClaimStatus= ClaimStatusEnum.PUKANG_INPUTING;

                String extraStore =  oldLog.getExtraStore();
                if(org.apache.commons.lang.StringUtils.isBlank(extraStore)){
                    throw new TpaBizException("无法定位操作人人信息");
                }
                JSONObject userInfo =     JSONObject.parseObject(extraStore);

                operatorId = userInfo.getString("operId");
                operatorName = userInfo.getString("operName");
                operatorGroupId = userInfo.getString("operOrgId");
                operatorGroupName = userInfo.getString("operOrgName");
            } else{
                if(StringUtils.isBlank(operatorId)){
                    throw new RuntimeException("初审自动分配结果为空");
                }
                toClaimStatus= ClaimStatusEnum.PUKANG_INPUTING;
            }
            claimService.setStatusField(updto, toClaimStatus);
            updto.setOperatorUserName(operatorName);
            updto.setOperatorUserId(operatorId);
            updto.setOperatorOrgId(operatorGroupId);
            updto.setOperatorOrgName(operatorGroupName);
//            updto.setSubmittingOperatorName(operatorName);
            claimService.updateClaim(updto, false, "InputStageService自动分配");

            //claimRepository.updateAndClearFields(updto,clearFieldList);

            trackLogService.addActionRecord(claim,"system",null, OperationTypeEnum.INPUT_CHECK_ASSIGN,
                    "自动分配录入操作人:"+operatorName);

        } else{
            //手工指定
            claimService.updateClaim(updto, false, "InputStageService手工分配");
        }

        return toClaimStatus;
    }
}
