package com.bone.tpa.claim.flow.stage;

import cn.hutool.core.date.DateUtil;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.flow.trigger.ApproveReviewToEndTrigger;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.ReportLogRequest;
import com.bone.tpa.push.service.PushClaimService;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.InsurancePushStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.enums.PkPushStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.util.PkJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReviewApproveStageService extends BaseStageService {
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
    private PushClaimService pushClaimService;


    @Transactional(rollbackFor = Exception.class)
    public void pageApproveAction(Long claimNumber){
        String operator = BizContextUtils.getUser();
        if(StringUtils.isBlank(operator)){
           throw new RuntimeException("operator is null");
        }

        Claim claim =  claimRepository.findById(claimNumber);

        Map<String,Object> traceLogExtraStore = new HashMap<>();
        traceLogExtraStore.put("operName",operator);
        String dateFormate = "yyyy-MM-dd HH:mm:ss";

        claimService.clearOperator(claim);
        trackLogService.addActionRecord(claim, operator, traceLogExtraStore,
                OperationTypeEnum.REVIEW_COMPLETE,"复核完成");

        ReportLogRequest reviewLogLogRequest = new ReportLogRequest();
        reviewLogLogRequest.setStage( ClaimStatusEnum.COMPLETE_AUDIT.getStage().getCode());
        reviewLogLogRequest.setOperatorName(operator);
        reviewLogLogRequest.setStatus("复核完成");
        reviewLogLogRequest.setEndTime(DateUtil.format(new Date(),dateFormate));




        claimService.setStatusField(claim, ClaimStatusEnum.COMPLETE_AUDIT);
        claim.setPkPushStatus(PkPushStatusEnum.WAITING_PUSH.getName());
        claim.setInsurancePushStatus(InsurancePushStatusEnum.WAITING_PUSH.getName());
        claimService.updateClaim(claim, false, "ReviewApproveStageService完成复核");

        pushClaimService.push(claim.getId(), null);


        SpringContextUtils.getBean(ApproveReviewToEndTrigger.class)
                .addJobAndTryFire(
                        PkJsonUtil.buildPkJson(
                                "claimNumber",claim.getId().toString(),
                                "operator", BizContextUtils.getUser()
                        ),3,3

                );
        //直接到终态
        claimService.notifyTpaChangeStatus(claim,new Date(),ClaimStatusEnum.COMPLETE_AUDIT,
                PkListUtil.asList( reviewLogLogRequest),
                operator,
                "完成复核","完成复核",true );
    }
}
