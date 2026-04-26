package com.bone.tpa.claim.flow.jump;

import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.claim.flow.stage.ApproveStageService;
import com.bone.tpa.claim.flow.FlowFireService;
import com.bone.tpa.claim.flow.trigger.ApproveLandTrigger;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.vo.ApproveConfigVO;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.util.PkJsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class ApproveFlowFireService  extends BaseFlowFireService implements FlowFireService {
    @Autowired
    private ApproveStageService approveStageService;
    /**
     * 向下推送流程
     *
     * @param claim
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void fire(Claim claim) {
        //进入初审
        Claim updto = new Claim();
        updto.setId(claim.getId());
        claimService.setStatusField(updto, ClaimStatusEnum.COMPLETE_INSPECTION);
        claimService.clearOperator(updto);
        claimService.updateClaim(updto, false, "ApproveFlowFireService.fire");
        claim = claimRepository.findById(claim.getId());

        //异步任务出发进入录入流程
        SpringContextUtils.getBean(ApproveLandTrigger.class).addJobAndTryFire(
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
        ClaimFlowConfigVO flowConfigVO =   flowConfigBiz.convertById(claim.getFlowConfigId());
        ApproveConfigVO appproveConfig = flowConfigVO.getApproveConfigVO();
        //saas圈流程
        approveStageService.inputDealerApply(claim,new Date());
        //发票查重
        claimService.checkSameInvoiceAsync(claim.getId());
    }
}
