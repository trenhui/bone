package com.bone.tpa.claim.flow.jump;

import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.flow.stage.QualityStageService;
import com.bone.tpa.claim.flow.FlowFireService;
import com.bone.tpa.claim.flow.trigger.QualityLandTrigger;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.vo.BaseFlowNodeVO;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.sdk.vo.QualityConfigVO;
import com.bone.tpa.util.PkJsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 质检流程方法
 */
@Service
public class QualityFlowFireService  extends BaseFlowFireService implements FlowFireService {
    @Autowired
    private ClaimTrackLogService trackLogService;
    @Autowired
    private ApproveFlowFireService approveFlowFireService;

    @Autowired
    private QualityStageService qualityStageService;
    /**
     * 向下推送流程
     *
     * @param claim
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void fire(Claim claim) {
        if(canJoin(claim)){
            //进入质检
            Claim updto = new Claim();
            updto.setId(claim.getId());
            claimService.setStatusField(updto, ClaimStatusEnum.INPUT_COMPLETE);
            claimService.clearOperator(updto);
            claimService.updateClaim(updto, false, "QualityFlowFireService.dealHalfInput");
            claim = claimRepository.findById(claim.getId());

            //异步任务出发进入审核流程
            SpringContextUtils.getBean(QualityLandTrigger.class).addJobAndTryFire(
                    PkJsonUtil.buildPkJson("claimNumber",claim.getId().toString()),
                    5,3);
        }else{

            trackLogService.addActionRecord(claim,"system",null, OperationTypeEnum.JUMP_OVER_QUALITY_CHECK,
                    "跳过质检");
            approveFlowFireService.fire(claim);
        }
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
        BaseFlowNodeVO qualityFlowNode =  flowConfigVO.getFlowConfigVO().getQualityFlowNode();
        boolean canJoin =   canJoinNode(claim,"质检",qualityFlowNode);
        return canJoin;
    }

    /**
     * 落地流程后的动作（一般记录一个异步任务)
     *
     * @param claim
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void doLandEvent(Claim claim) {
        ClaimFlowConfigVO flowConfigVO =   flowConfigBiz.convertById(claim.getFlowConfigId());
        QualityConfigVO qualityConfigVO = flowConfigVO.getQualityConfigVO();
        //saas全流程
        dealAllFlow(claim,qualityConfigVO);

        claimService.checkSameInvoiceAsync(claim.getId());
    }

    private void dealAllFlow(Claim claim,QualityConfigVO qualityConfigVO){
        //质检主要是分配人员有区别
        qualityStageService.inputDealerApply(claim,new Date());
    }
}
