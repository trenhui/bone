package com.bone.tpa.claim.flow.jump;

import com.bone.core.util.BizContextUtils;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.claim.application.ClaimImageApplicationService;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimImageService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.flow.stage.PreCheckStageService;
import com.bone.tpa.claim.flow.FlowFireService;
import com.bone.tpa.claim.flow.trigger.PrecheckLandTrigger;
import com.bone.tpa.facade.vo.InsuranceCompanyImageVO;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.dao.ClaimImageRepository;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.BaseFlowNodeVO;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.sdk.vo.PreCheckConfigVO;
import com.bone.tpa.task.impl.AutoPreExameTrigger;
import com.bone.tpa.util.PkJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 初审阶段
 */
@Service
public class PrecheckFlowFireService extends BaseFlowFireService implements FlowFireService {
    @Autowired
    private AutoPreExameTrigger autoPreExameTrigger;

    @Autowired
    private    ClaimTrackLogService trackLogService;

    @Autowired
    private InputFlowFireService inputFlowFireService;
    @Autowired
    private   ClaimImageApplicationService claimImageApplicationService;

    @Autowired
    private PreCheckStageService preCheckService;
    @Autowired
    private ClaimImageService claimImageService;

    @Autowired
    private ClaimImageRepository claimImageRepository;


    @Autowired
    private ClaimFlowConfigBiz flowConfigBiz;
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
            updto.setStatus(ClaimStatusEnum.SIGNED.getCode());
            updto.setStage(ClaimStatusEnum.SIGNED.getStage().getCode());
            claimService.clearOperator(updto);
            claimService.updateClaim(updto, false, "PrecheckFlowFireService.fire");
            //异步任务出发进入初审流程
            claim = claimRepository.findById(claim.getId());
            SpringContextUtils.getBean(PrecheckLandTrigger.class)
                    .addJobAndTryFire(
                            PkJsonUtil.buildPkJson(
                                    "claimNumber",claim.getId().toString()
                            ),
                    5,3);
        }else{

            //跳过的话，进行一次自动化分类
            List<InsuranceCompanyImageVO> insuranceCompanyImageVOList = claimImageApplicationService.getCompanyImageVO(claim.getInsuranceName());

            //保司分类, 普康分类
            Map<String, String> imageTypeMap = insuranceCompanyImageVOList.stream().collect(Collectors.toMap(InsuranceCompanyImageVO::getImageClassifyCode, InsuranceCompanyImageVO::getImageMapCode));
            List<ClaimImage>  imageList =  claimImageService.getListByClaimNumber(claim.getId());
            for(ClaimImage claimImage : imageList){
                String baosiType =   claimImage.getImageType();
                if(StringUtils.isBlank(baosiType)){
                    continue;
                }
                String pkType =   imageTypeMap.get(baosiType);
                if(StringUtils.isBlank(pkType)){
                    continue;
                }
                //更新普康分类
                ClaimImage upDto = new ClaimImage();
                upDto.setId(claimImage.getId());
                upDto.setImagePkType(pkType);
                claimImageRepository.update(upDto);

            }

            trackLogService.addActionRecord(claim,"system",null, OperationTypeEnum.JUMP_OVER_PRECHECK,

                    "跳过初审");
            inputFlowFireService.fire(claim);
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
        BaseFlowNodeVO precheckFlowConfig =  flowConfigVO.getFlowConfigVO().getPreCheckFlowNode();
        boolean canJoin =   canJoinNode(claim,"初审",precheckFlowConfig);
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
        //https://lanhuapp.com/web/#/item/project/product?tid=063e3219-9b22-43d6-85a3-cc11e191acd9&pid=a903a9dd-36ae-4673-901d-5b4b4922d6ca&versionId=d1c6b725-4343-4a36-bdd1-9dc0bd9cf132&docId=95ff6045-c48a-45ae-8dd7-f3d7edf54197&docType=axure&pageId=f0ac456dc28149e78e163fbc395558e9&image_id=95ff6045-c48a-45ae-8dd7-f3d7edf54197&parentId=1f67a83201894a73a13b5bbb5300cfb7
        ClaimFlowConfigVO flowConfigVO =   flowConfigBiz.convertById(claim.getFlowConfigId());
        PreCheckConfigVO precheckConfigVo =     flowConfigVO.getPreCheckConfigVO();
        dealAllFlow(claim,precheckConfigVo);

    }


    /**
     * 处理
     * cfgBizType == 2
     * @param claim
     */
    private void dealHalfInput(Claim claim,PreCheckConfigVO precheckConfigVo){
        Long claimNumber = claim.getId();
        String autoTag =   precheckConfigVo.getAutoTag();
        Claim upDto = new Claim();
        upDto.setId(claim.getId());
        upDto.setBizIdentityCode(claim.getBizIdentityCode());
        //清空操作人
        clearOperator(upDto);

        if (StringUtils.equals("1",autoTag)){
            //自动化作业
            commonLogService.addClaimLogAsync(claim.getId(), CommonLogType.FROM_TPA_LOG,
                    "tpa签收赔案下发到saas初审,claimNumber:{},进入自动化作业",claim.getId());
            upDto.setStatus(ClaimStatusEnum.ROBOT_PRE_ADUIT.getCode());
            upDto.setStatusSub(ClaimStatusEnum.ROBOT_PRE_ADUIT.getSubStatus());
            upDto.setStage(ClaimStatusEnum.ROBOT_PRE_ADUIT.getStage().getCode());

            //记录一个异步任务
            //记录一个自动化初审的任务，3秒后线程池执行
            autoPreExameTrigger.addJobAndTryFire(claim.getId().toString(),
                    1,3
            );


        }else{
            //非自动化作业
            //等待tpa分配
            commonLogService.addClaimLogAsync(claimNumber, CommonLogType.FROM_TPA_LOG,
                    "tpa签收赔案下发到saas初审,claimNumber:{},进入非自动化作业", claimNumber);
            upDto.setStatus(ClaimStatusEnum.WAITING_PRE_ADUIT.getCode());
            upDto.setStage(ClaimStatusEnum.WAITING_PRE_ADUIT.getStage().getCode());
            upDto.setStatusSub(ClaimStatusEnum.WAITING_PRE_ADUIT.getSubStatus());

        }
        claimService.updateClaim(upDto, false, "PrecheckFlowFireService.dealHalfInput");
        commonLogService.addClaimLogAsync(claimNumber, CommonLogType.FROM_TPA_LOG,
                "tpa签收赔案下发到saas初审,claimNumber:{} ",claimNumber );
    }


    private void dealAllFlow(Claim claim,PreCheckConfigVO precheckConfigVo){
        String autoTag =   precheckConfigVo.getAutoTag();
        /**
         * 0 先自动分类再人工确认
         * 1 仅自动分类
         * 2 仅人工分类
         */
        Claim upDto = new Claim();
        upDto.setId(claim.getId());
        upDto.setBizIdentityCode(claim.getBizIdentityCode());
        //清空操作人
        clearOperator(upDto);
        String categoryType = precheckConfigVo.getCategoryType();
        if("2".equals(categoryType) || "0".equals(autoTag)){
            //仅人工分类或者不开启自动化，都走人工
            //todohelei
            preCheckService.preCheckDealerApply(claim, new Date());
        }else{
            //记录自动化初审的异步任务
            commonLogService.addClaimLogAsync(claim.getId(), CommonLogType.FROM_TPA_LOG,
                    "saas全流程进入自动化初审,claimNumber:{},进入自动化作业",claim.getId());
            upDto.setStatus(ClaimStatusEnum.ROBOT_PRE_ADUIT.getCode());
            upDto.setStatusSub(ClaimStatusEnum.ROBOT_PRE_ADUIT.getSubStatus());
            upDto.setStage(ClaimStatusEnum.ROBOT_PRE_ADUIT.getStage().getCode());
            /*claimService.notifyTpaChangeStatus(claim,new Date(), ClaimStatusEnum.ROBOT_PRE_ADUIT,
                    PkListUtil.newArrayList(), BizContextUtils.getUser(),"进入自动化录入"
                    ,"进入自动化录入");*/
            //记录一个异步任务
            //记录一个自动化初审的任务，3秒后线程池执行
            SpringContextUtils.getBean(AutoPreExameTrigger.class).addJobAndTryFire(claim.getId().toString(),
                    1,3
            );
        }
    }
}
