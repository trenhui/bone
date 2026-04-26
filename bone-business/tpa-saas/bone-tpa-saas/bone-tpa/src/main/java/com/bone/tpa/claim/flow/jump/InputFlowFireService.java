package com.bone.tpa.claim.flow.jump;

import com.bone.core.util.BizContextUtils;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.flow.stage.InputStageService;
import com.bone.tpa.claim.flow.FlowFireService;
import com.bone.tpa.claim.flow.trigger.InputLandTrigger;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.vo.BaseFlowNodeVO;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.sdk.vo.InputConfigVO;
import com.bone.tpa.task.impl.AutoInputTrigger;
import com.bone.tpa.util.PkJsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class InputFlowFireService extends BaseFlowFireService implements FlowFireService {
    @Autowired
    private  QualityFlowFireService qualityFlowFireService;
    @Autowired
    private ClaimTrackLogService trackLogService;

    @Autowired
    private InputStageService inputStageService;

    /**
     * 向下推送流程
     *
     * @param claim
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void fire(Claim claim) {
        if(canJoin(claim)){
            //进入初审
            Claim updto = new Claim();
            updto.setId(claim.getId());
            claimService.setStatusField(updto, ClaimStatusEnum.COMPLETE_PRE_ADUIT);
            claimService.clearOperator(updto);
            claimService.updateClaim(updto, false, "InputFlowFireService.fire");
            claim = claimRepository.findById(claim.getId());

            //异步任务出发进入录入流程
            SpringContextUtils.getBean(InputLandTrigger.class).addJobAndTryFire(
                PkJsonUtil.buildPkJson("claimNumber",claim.getId().toString()),
                    5,3);
        }else{
            trackLogService.addActionRecord(claim,"system",null, OperationTypeEnum.JUMP_OVER_INPUT,

                    "跳过录入");



            qualityFlowFireService.fire(claim);
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
        BaseFlowNodeVO inputFlowNode =  flowConfigVO.getFlowConfigVO().getInputFlowNode();
        boolean canJoin =   canJoinNode(claim,"录入",inputFlowNode);
        return canJoin;
    }

    /**
     * 落地流程后的动作（一般记录一个异步任务)
     *
     * @param claim
     */
    @Override
    public void doLandEvent(Claim claim) {
        ClaimFlowConfigVO flowConfigVO =   flowConfigBiz.convertById(claim.getFlowConfigId());
        InputConfigVO inputConfigVO = flowConfigVO.getInputConfigVO();


            //saas圈流程
          dealAllFlow(claim,inputConfigVO);
    }




    private void dealAllFlow(Claim claim, InputConfigVO configVO){
        /**
         * 0 先技力再传统录入
         * 1 仅人力录入
         * 2 仅技力录入(在质检环境补充)
         */
        String inputType =   configVO.getInputType();
        /**
         * 是否自动化
         * 0 否
         * 1 是
         */
        String autoTag = configVO.getAutoTag();

        if("1".equals(inputType)||"0".equals(autoTag)){
            //人力录入
            inputStageService.inputDealerApply(claim,new Date());
        }else{
            //自动化录入
            //通知tpa改变状态
            claimService.notifyTpaChangeStatus(claim,new Date(),
                    ClaimStatusEnum.ORC_INPUTING,PkListUtil.newArrayList(),
                    BizContextUtils.getUser(),"自动化录入中","自动化录入中",true);
            //发起自动化录入的任务
            SpringContextUtils.getBean(AutoInputTrigger.class).addJobAndTryFire(claim.getId().toString(),
                    1, 3
            );
        }
    }
}
