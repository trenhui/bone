package com.bone.tpa.core.service.impl;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.core.service.ClaimFlowService;
import com.bone.tpa.sdk.claim.enums.ClaimFlowStatus;
import com.bone.tpa.sdk.claim.model.ClaimFlowConfig;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ClaimFlowServiceImpl implements ClaimFlowService {
    @Autowired
    private ClaimFlowConfigBiz flowConfigBiz;

    /**
     * 获取草稿态的流程配置
     *
     * @param bizIdentityCode
     * @return
     */
    @Override
    public ClaimFlowConfigVO getClaimFlowConfig(String bizIdentityCode,ClaimFlowStatus status)  {
        ClaimFlowConfig config =  flowConfigBiz.getByIdentityCodeAndStatus(bizIdentityCode, status);
        ClaimFlowConfigVO configVO  =  flowConfigBiz.convert(config);
        if(config == null){
            configVO.setBizIdentityCode(bizIdentityCode);
        }
        fillDefault(configVO);

        return configVO;
    }

    private void fillDefault(ClaimFlowConfigVO configVO){
        fillPrecheckNode(configVO.getFlowConfigVO());
        //填充录入节点
        fillInputNode(configVO.getFlowConfigVO());
        // 质检阶段
        fillQualityNode(configVO.getFlowConfigVO());
        //审核阶段
        fillApproveNode(configVO.getFlowConfigVO());
        //复核阶段
        fillReApproveNode(configVO.getFlowConfigVO());
    }

    @Override
    public ClaimFlowConfigVO getClaimFlowById(Long flowConfigId,String bizIdentityCode) {
        ClaimFlowConfig config =  flowConfigBiz.getById(flowConfigId);
        ClaimFlowConfigVO configVO  =  flowConfigBiz.convert(config);
        if(config == null){
            configVO.setBizIdentityCode(bizIdentityCode);
        }
        fillDefault(configVO);
        return configVO;
    }


    /**
     * 是否进入复核阶段
     * @param vo
     */
    private void fillReApproveNode(FlowConfigVO vo){
        BaseFlowNodeVO approveFlowNode =  vo.getApproveCheckFlowNode();
        List<JumpTypeVO> jumpTypeVOList = approveFlowNode.getJumpTypeList();
        Map<String, JumpTypeVO> jumpTypeMap = PkListUtil.listToMap(jumpTypeVOList,JumpTypeVO::getType);
        //需要重新组装下条件，因为可能会有新的顺序
        List<JumpTypeVO>  rs = new ArrayList<>();
        JumpTypeVO inType =   jumpTypeMap.get(JumpTypeVO.IN_TYPE);
        if( inType == null){
            rs.add( JumpTypeVO.getInConditionVo());
        }else{
            rs.add(inType);
        }

        JumpTypeVO outType =   jumpTypeMap.get(JumpTypeVO.OUT_TYPE);
        if( outType == null){
            rs.add( JumpTypeVO.getOutConditionVo());
        }else {
            rs.add(outType);
        }



        JumpTypeVO orType =    jumpTypeMap.get(JumpTypeVO.CONDITION_OR_TYPE);
        if( orType == null){
            orType = JumpTypeVO.getDefaultOrConditionVo();
        }
        rs.add(orType);
        List<FlowConditionVO>  orConditionList =   orType.getConditionList();
        if( orConditionList == null){
            orConditionList = new ArrayList<>();
            orType.setConditionList(orConditionList);
        }
        //封闭一下，虽然这个判断没啥用，但是可以做下封闭
        if(orConditionList!= null){
            List<FlowConditionVO>  orConditionListNew =new ArrayList<>();
            orType.setConditionList(orConditionListNew);
            orConditionListNew.add(returnBackClaimCondition(orConditionList));
            orConditionListNew.add(tpaReApproveRuleCondition(orConditionList));

            FlowConditionVO offlineCondition =   offlineCondition(orConditionList);
            orConditionListNew.add(offlineCondition);

            FlowConditionVO onlinelineCondition =   onlinelineCondition(orConditionList);
            orConditionListNew.add(onlinelineCondition);

            FlowConditionVO autoPrecheckCondition =   autoPrecheckCondition(orConditionList);
            orConditionListNew.add(autoPrecheckCondition);

            FlowConditionVO autoInputCondition =   autoInputCondition(orConditionList);
            orConditionListNew.add(autoInputCondition);

            orConditionListNew.add((autoApproveCondition(orConditionList)));
            //脚本条件
            orConditionListNew.add(sciptCondition(orConditionList));
        }


        JumpTypeVO andType =    jumpTypeMap.get(JumpTypeVO.CONDITION_AND_TYPE);
        if( andType == null){
            andType = JumpTypeVO.getDefaultAndConditionVo();
        }
        rs.add(andType);
        List<FlowConditionVO>  andConditionList =   andType.getConditionList();
        if( andConditionList == null){
            andConditionList = new ArrayList<>();
            andType.setConditionList(andConditionList);
        }
        if(andConditionList!= null){
            List<FlowConditionVO>  andConditionListNew =new ArrayList<>();
            andType.setConditionList(andConditionListNew);

            andConditionListNew.add(returnBackClaimCondition(andConditionList));
            andConditionListNew.add(tpaReApproveRuleCondition(andConditionList));


            FlowConditionVO offlineCondition =   offlineCondition(andConditionList);
            andConditionListNew.add(offlineCondition);

            FlowConditionVO onlinelineCondition =   onlinelineCondition(andConditionList);
            andConditionListNew.add(onlinelineCondition);


            FlowConditionVO autoPrecheckCondition =   autoPrecheckCondition(andConditionList);
            andConditionListNew.add(autoPrecheckCondition);


            FlowConditionVO autoInputCondition =   autoInputCondition(andConditionList);
            andConditionListNew.add(autoInputCondition);
            //自动化审核条件
            andConditionListNew.add((autoApproveCondition(andConditionList)));
            //脚本条件
            andConditionListNew.add(sciptCondition(andConditionList));

        }


        fillDefaultChoose(rs);
        approveFlowNode.setJumpTypeList(rs);
    }

    /**
     * 线下赔案
     * @param existConditionList
     * @return
     */

    private FlowConditionVO offlineCondition(List<FlowConditionVO>  existConditionList ){
        Map<String,FlowConditionVO> beanExistMap =   PkListUtil.listToMap(existConditionList,FlowConditionVO::getBeanName);
        //线下赔案进入
        String channelOfflineBeanName  = "channelOffline";
        FlowConditionVO existConfig =    beanExistMap.get(channelOfflineBeanName);
        if( existConfig == null){
            existConfig = FlowConditionVO.normalCondition("线下赔案", channelOfflineBeanName);

        }
        return existConfig;

    }

    /**
     * 自动化审核案件
     * @param existConditionList
     * @return
     */
    private FlowConditionVO autoApproveCondition(List<FlowConditionVO>  existConditionList ){
        Map<String,FlowConditionVO> beanExistMap =   PkListUtil.listToMap(existConditionList,FlowConditionVO::getBeanName);
        //线下赔案进入
        String autoApproveCondition  = "autoApproveCondition";
        FlowConditionVO existConfig =    beanExistMap.get(autoApproveCondition);
        if( existConfig == null){
            existConfig = FlowConditionVO.normalCondition("自动化审核案件", autoApproveCondition);

        }
        return existConfig;

    }

    /**
     * tpa 的是否进入复核的规则
     * @param existConditionList
     * @return
     */
    private FlowConditionVO  tpaReApproveRuleCondition(List<FlowConditionVO>  existConditionList ){
        Map<String,FlowConditionVO> beanExistMap =   PkListUtil.listToMap(existConditionList,FlowConditionVO::getBeanName);
        String tpaReviewApproveRule  = "tpaReviewApproveRule";
        FlowConditionVO existConfig =    beanExistMap.get(tpaReviewApproveRule);
        if( existConfig == null){
            existConfig =  FlowConditionVO.normalCondition("tpa复核规则准入", tpaReviewApproveRule);
        }
        return existConfig;
    }

    /**
     * 是否自动化审核条件
     * @param existConditionList
     */
    private FlowConditionVO autoInputCondition(List<FlowConditionVO>  existConditionList){
        Map<String,FlowConditionVO> beanExistMap =   PkListUtil.listToMap(existConditionList,FlowConditionVO::getBeanName);
        String  returnBackclaimBeanName = "autoInputCondition";
        FlowConditionVO existConfig =    beanExistMap.get(returnBackclaimBeanName);
        if( existConfig == null){
            existConfig =  FlowConditionVO.normalCondition("自动化录入的赔案",
                    returnBackclaimBeanName);
        }
        return existConfig;
    }


    private FlowConditionVO sciptCondition(List<FlowConditionVO>  existConditionList){
        Map<String,FlowConditionVO> beanExistMap =   PkListUtil.listToMap(existConditionList,FlowConditionVO::getBeanName);
        String  scriptBeanName = "scriptCondition";
        FlowConditionVO existConfig =    beanExistMap.get(scriptBeanName);
        if( existConfig == null){
            existConfig =  FlowConditionVO.normalCondition("脚本规则",
                    scriptBeanName);
        }
        existConfig.setType(1);
        return existConfig;
    }


    /**
     * 自动化初审的
     * @param existConditionList
     * @return
     */
    private FlowConditionVO autoPrecheckCondition(List<FlowConditionVO>  existConditionList){
        Map<String,FlowConditionVO> beanExistMap =   PkListUtil.listToMap(existConditionList,FlowConditionVO::getBeanName);
        String  returnBackclaimBeanName = "autoPrecheckCondition";
        FlowConditionVO existConfig =    beanExistMap.get(returnBackclaimBeanName);
        if( existConfig == null){
            existConfig =  FlowConditionVO.normalCondition("自动化初审赔案", returnBackclaimBeanName);
        }
        return existConfig;
    }

    /**
     * 退回节点
     * @param existConditionList
     * @return
     */
    private FlowConditionVO returnBackClaimCondition(List<FlowConditionVO>  existConditionList ){
        Map<String,FlowConditionVO> beanExistMap =   PkListUtil.listToMap(existConditionList,FlowConditionVO::getBeanName);
        //退回的赔案
        String  returnBackclaimBeanName = "returnBackclaimBeanName";
        FlowConditionVO existConfig =    beanExistMap.get(returnBackclaimBeanName);
        if( existConfig == null){
            existConfig =  FlowConditionVO.normalCondition( "退回的赔案", returnBackclaimBeanName);

        }
        return existConfig;
    }

    /**
     * 线上赔案
     * @param existConditionList
     * @return
     */
    private FlowConditionVO onlinelineCondition(List<FlowConditionVO>  existConditionList ){
        Map<String,FlowConditionVO> beanExistMap =   PkListUtil.listToMap(existConditionList,FlowConditionVO::getBeanName);
        String channelOnlineBeanName  = "channelOnline";
        FlowConditionVO existConfig =    beanExistMap.get(channelOnlineBeanName);
        if( existConfig == null){
            existConfig =  FlowConditionVO.normalCondition( "线上赔案", channelOnlineBeanName);

        }
        return existConfig;
    }
    private void fillApproveNode(FlowConfigVO vo){
        BaseFlowNodeVO approveFlowNode =  vo.getApproveFlowNode();
        List<JumpTypeVO>  jumpTypeVOList = approveFlowNode.getJumpTypeList();
        Map<String, JumpTypeVO> jumpTypeMap = PkListUtil.listToMap(jumpTypeVOList,JumpTypeVO::getType);
        //需要重新组装下条件，因为可能会有新的顺序
        List<JumpTypeVO>  rs = new ArrayList<>();
        JumpTypeVO inType =   jumpTypeMap.get(JumpTypeVO.IN_TYPE);
        if( inType == null){
            rs.add( JumpTypeVO.getInConditionVo());
        }else{
            rs.add(inType);
        }

        fillDefaultChoose(rs);
        approveFlowNode.setJumpTypeList(rs);
    }

    private void fillQualityNode(FlowConfigVO vo){
        BaseFlowNodeVO qualityFlowNode =  vo.getQualityFlowNode();
        List<JumpTypeVO>  jumpTypeVOList = qualityFlowNode.getJumpTypeList();
        Map<String, JumpTypeVO> jumpTypeMap = PkListUtil.listToMap(jumpTypeVOList,JumpTypeVO::getType);
        //需要重新组装下条件，因为可能会有新的顺序
        List<JumpTypeVO>  rs = new ArrayList<>();
        JumpTypeVO inType =   jumpTypeMap.get(JumpTypeVO.IN_TYPE);
        if( inType == null){
            rs.add( JumpTypeVO.getInConditionVo());
        }else{
            rs.add(inType);
        }

        JumpTypeVO outType =   jumpTypeMap.get(JumpTypeVO.OUT_TYPE);
        if( outType == null){
            rs.add( JumpTypeVO.getOutConditionVo());
        }else {
            rs.add(outType);
        }

        JumpTypeVO orType =    jumpTypeMap.get(JumpTypeVO.CONDITION_OR_TYPE);
        if( orType == null){
            orType = JumpTypeVO.getDefaultOrConditionVo();
        }
        rs.add(orType);
        List<FlowConditionVO>  orConditionList =   orType.getConditionList();
        if( orConditionList == null){
            orConditionList = new ArrayList<>();
            orType.setConditionList(orConditionList);
        }
        //封闭一下，虽然这个判断没啥用，但是可以做下封闭
        if(orConditionList!= null){
            List<FlowConditionVO>  orConditionListNew =new ArrayList<>();
            orType.setConditionList(orConditionListNew);

            FlowConditionVO offlineCondition =   offlineCondition(orConditionList);
            orConditionListNew.add(offlineCondition);

            FlowConditionVO onlinelineCondition =   onlinelineCondition(orConditionList);
            orConditionListNew.add(onlinelineCondition);


            FlowConditionVO autoPrecheckCondition =   autoPrecheckCondition(orConditionList);
            orConditionListNew.add(autoPrecheckCondition);

            FlowConditionVO autoInputCondition =   autoInputCondition(orConditionList);
            orConditionListNew.add(autoInputCondition);

            //脚本条件
            orConditionListNew.add(sciptCondition(orConditionList));


        }


        ///and

        JumpTypeVO andType =    jumpTypeMap.get(JumpTypeVO.CONDITION_AND_TYPE);
        if( andType == null){
            andType = JumpTypeVO.getDefaultAndConditionVo();
        }
        rs.add(andType);
        List<FlowConditionVO>  andConditionList =   andType.getConditionList();
        if( andConditionList == null){
            andConditionList = new ArrayList<>();
            andType.setConditionList(andConditionList);
        }

        if(andConditionList!= null){
            List<FlowConditionVO>  andConditionListNew =new ArrayList<>();
            andType.setConditionList(andConditionListNew);

            FlowConditionVO offlineCondition =   offlineCondition(andConditionList);
            andConditionListNew.add(offlineCondition);

            FlowConditionVO onlinelineCondition =   onlinelineCondition(andConditionList);
            andConditionListNew.add(onlinelineCondition);


            FlowConditionVO autoPrecheckCondition =   autoPrecheckCondition(andConditionList);
            andConditionListNew.add(autoPrecheckCondition);


            FlowConditionVO autoInputCondition =   autoInputCondition(andConditionList);
            andConditionListNew.add(autoInputCondition);

            //脚本条件
            andConditionListNew.add(sciptCondition(andConditionList));

        }

        fillDefaultChoose(rs);
        qualityFlowNode.setJumpTypeList(rs);
    }

    /**
     * 是否跳过录入的流程配置
     * @param vo
     */
    private void fillInputNode(FlowConfigVO vo){
        BaseFlowNodeVO inputFlowNode =  vo.getInputFlowNode();
        List<JumpTypeVO>  jumpTypeVOList = inputFlowNode.getJumpTypeList();
        Map<String, JumpTypeVO> jumpTypeMap = PkListUtil.listToMap(jumpTypeVOList,JumpTypeVO::getType);
        //需要重新组装下条件，因为可能会有新的顺序
        List<JumpTypeVO>  rs = new ArrayList<>();
        JumpTypeVO inType =   jumpTypeMap.get(JumpTypeVO.IN_TYPE);
        if( inType == null){
            rs.add( JumpTypeVO.getInConditionVo());
        }else{
            rs.add(inType);
        }

        JumpTypeVO outType =   jumpTypeMap.get(JumpTypeVO.OUT_TYPE);
        if( outType == null){
            rs.add( JumpTypeVO.getOutConditionVo());
        }else {
            rs.add(outType);
        }



        fillDefaultChoose(rs);
        inputFlowNode.setJumpTypeList(rs);

    }

    private void fillDefaultChoose(List<JumpTypeVO>  jumpTypeVOList){
        if(PkListUtil.isEmpty(jumpTypeVOList)){
            return;
        }
        boolean choosen = false;
        for(JumpTypeVO jumpType:jumpTypeVOList){
            if(jumpType!= null && jumpType.getStatus()!= null &&
                    jumpType.getStatus() == 1){
                choosen = true;
                break;
            }
        }
        if(!choosen){
            //默认选一个
            jumpTypeVOList.get(0).setStatus(1);
        }
    }

    /**
     * 是否跳过初审的流程配置
     * @param vo
     */
    private void fillPrecheckNode(FlowConfigVO vo){
        BaseFlowNodeVO preCheckVo =  vo.getPreCheckFlowNode();
        List<JumpTypeVO>  jumpTypeVOList = preCheckVo.getJumpTypeList();
        Map<String, JumpTypeVO> jumpTypeMap = PkListUtil.listToMap(jumpTypeVOList,JumpTypeVO::getType);
        //需要重新组装下条件，因为可能会有新的顺序
        List<JumpTypeVO>  rs = new ArrayList<>();
        JumpTypeVO inType =   jumpTypeMap.get(JumpTypeVO.IN_TYPE);
        if( inType == null){
            rs.add( JumpTypeVO.getInConditionVo());
        }else{
            rs.add(inType);
        }

        JumpTypeVO outType =   jumpTypeMap.get(JumpTypeVO.OUT_TYPE);
        if( outType == null){
            rs.add( JumpTypeVO.getOutConditionVo());
        }else {
            rs.add(outType);
        }
        //or condition
        JumpTypeVO orType =    jumpTypeMap.get(JumpTypeVO.CONDITION_OR_TYPE);
        if( orType == null){
            orType = JumpTypeVO.getDefaultOrConditionVo();
        }
        List<FlowConditionVO>  orConditionList =   orType.getConditionList();
        if( orConditionList == null){
            orConditionList = new ArrayList<>();
            orType.setConditionList(orConditionList);
        }
        //封闭一下，虽然这个判断没啥用，但是可以做下封闭
        if(orConditionList!= null){
            List<FlowConditionVO>  orConditionListNew =new ArrayList<>();
            orType.setConditionList(orConditionListNew);
            FlowConditionVO offlineCondition =   offlineCondition(orConditionList);
            orConditionListNew.add(offlineCondition);

            FlowConditionVO onlinelineCondition =   onlinelineCondition(orConditionList);
            orConditionListNew.add(onlinelineCondition);

            orConditionListNew.add(sciptCondition(orConditionList));

        }
        rs.add(orType);

//and condition
        JumpTypeVO andType =    jumpTypeMap.get(JumpTypeVO.CONDITION_AND_TYPE);
        if( andType == null){
            andType = JumpTypeVO.getDefaultAndConditionVo();
        }
        List<FlowConditionVO>  andConditionList =   andType.getConditionList();
        if( andConditionList == null){
            andConditionList = new ArrayList<>();
            andType.setConditionList(andConditionList);
        }
        //封闭一下，虽然这个判断没啥用，但是可以做下封闭
        if(andConditionList!= null){
            List<FlowConditionVO>  andConditionListNew =new ArrayList<>();
            andType.setConditionList(andConditionListNew);
            FlowConditionVO offlineCondition =   offlineCondition(andConditionList);
            andConditionListNew.add(offlineCondition);

            FlowConditionVO onlinelineCondition =   onlinelineCondition(andConditionList);
            andConditionListNew.add(onlinelineCondition);

            andConditionListNew.add(sciptCondition(orConditionList));

        }
        rs.add(andType);

        fillDefaultChoose(rs);
        preCheckVo.setJumpTypeList(rs);


    }
}
