package com.bone.tpa.claim.flow.jump;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.flow.jumpcondition.JumpConditionIntterface;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.EventOtherRequest;
import com.bone.tpa.facade.request.TpaAddLogRequest;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.BaseFlowNodeVO;
import com.bone.tpa.sdk.vo.FlowConditionVO;
import com.bone.tpa.sdk.vo.JumpTypeVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BaseFlowFireService {
    @Autowired
    protected ClaimFlowConfigBiz flowConfigBiz;
    @Autowired
    protected ClaimRepository claimRepository;
    @Autowired
    protected ClaimService claimService;


    @Autowired
    protected CommonLogService commonLogService;


    @Autowired
    protected TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    protected ClaimToTpaChangeService toTpaChangeService;



    /**
     * 清空操作人
     * @param upDto
     */
    protected void clearOperator(Claim upDto){
        upDto.setOperatorUserName("");
        upDto.setOperatorUserId("");
        upDto.setOperatorOrgId("");
        upDto.setOperatorOrgName("");
    }



    protected  boolean canJoinNode(Claim claim,String stage ,BaseFlowNodeVO nodeConfig){
        if( nodeConfig == null){
            commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                    "{} 阶段跳过策略判断：进入节点，因为nodeConfig配置为空,",stage);
            return true;
        }
        if(PkListUtil.isEmpty(nodeConfig.getJumpTypeList())){
            commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                    "{} 阶段跳过策略判断：进入节点，因为getJumpTypeList配置为空,",stage);
            return true;
        }

        List<JumpTypeVO>  jumpTypeVOList = nodeConfig.getJumpTypeList();
        JumpTypeVO activeJump = PkListUtil.first(  jumpTypeVOList.stream().filter(t->t.getStatus()==1).
                collect(Collectors.toList()));
        if( activeJump == null){
            commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                    "{} 阶段跳过策略判断：进入节点，因为activeJump配置为空,",stage);
            return true;
        }
        String  jumpType = activeJump.getType();
        if(JumpTypeVO.IN_TYPE.equals(jumpType)){
            commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                    "{} 阶段跳过策略判断：进入节点，配置必须进入,",stage);
            return true;
        }
        if(JumpTypeVO.OUT_TYPE.equals(jumpType)){
            commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                    "{} 阶段跳过策略判断：跳过节点，配置跳过,",stage);
            return false;
        }
        if(JumpTypeVO.CONDITION_AND_TYPE.equals(jumpType)){
            List<FlowConditionVO>  conditionVOList =   activeJump.getConditionList();
            if(PkListUtil.isEmpty(conditionVOList)){
                commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                        "{} 阶段跳过策略判断,全部符合条件则进入，返回fasle，因为所选条件为空",stage);

                return false;
            }
            boolean ret = true;
            if(PkListUtil.isEmpty(conditionVOList)){
                return false;
            }
            conditionVOList = conditionVOList.stream().filter(t->t.getStatus()==1).collect(Collectors.toList());
            if(PkListUtil.isEmpty(conditionVOList)){
                //如果没有激活的条件，则返回false
                return false;
            }
            for(FlowConditionVO conditionVO : conditionVOList){

               if(conditionVO.getType() == 0){
                   //普通条件
                   String beanName = conditionVO.getBeanName();
                   if(StringUtils.isBlank(beanName)){
                       continue;
                   }
                   //SpringContextUtils.getBeansOfType(JumpConditionIntterface.class).en
                   JumpConditionIntterface dealBean =     getJumpConditionIntterfaceByName(beanName);
                   if( dealBean == null){
                       commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                               "{} 阶段跳过策略判断,全部符合条件则进入，有一个条件判断返回false:{}",stage,dealBean);
                       return false;
                   }
                    ret = ret &&  dealBean.isMatch(claim,conditionVO);
                   if( !ret){
                       commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                               "{} 阶段跳过策略判断,全部符合条件则进入，有一个条件判断返回false:{}",stage,dealBean);


                       return ret;
                   }
               }else if(conditionVO.getType() == 1){
                   // 脚本条件
                   String scriptBeanName = "scriptCondition";
                   JumpConditionIntterface dealBean =     getJumpConditionIntterfaceByName(scriptBeanName);
                   if( dealBean == null){
                       commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                               "{} 阶段跳过策略判断,全部符合条件则进入，脚本判断返回false:{}",stage,dealBean);


                       return false;
                   }
                   ret = ret &&  dealBean.isMatch(claim,conditionVO);
                   if( !ret){
                       commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                               "{} 阶段跳过策略判断,全部符合条件则进入，脚本判断返回false:{}",stage,dealBean);


                       return ret;
                   }
               }else{
                   return  false;
               }
            }
            commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                    "{} 阶段跳过策略判断,全部符合条件则进入，全部条件符合进入节点",stage);


            return ret;
        }




        if(JumpTypeVO.CONDITION_OR_TYPE.equals(jumpType)){
            List<FlowConditionVO>  conditionVOList =   activeJump.getConditionList();
            if(PkListUtil.isEmpty(conditionVOList)){
                commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                        "{} 阶段跳过策略判断,符合任一条件则进入，返回fasle，因为所选条件为空",stage);

                return false;
            }


            conditionVOList = conditionVOList.stream().filter(t->t.getStatus()==1).collect(Collectors.toList());
            if(PkListUtil.isEmpty(conditionVOList)){
                //如果没有激活的条件，则返回false
                commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                        "{} 阶段跳过策略判断,符合任一条件则进入，返回fasle，因为所选条件为空",stage);

                return false;
            }
            boolean ret = false;
            for(FlowConditionVO conditionVO : conditionVOList){
                Integer conditionStatus =  conditionVO.getStatus();
                if( conditionStatus == null || conditionStatus == 0){
                    continue;
                }
                if(conditionVO.getType() == 0){
                    //普通条件
                    String beanName = conditionVO.getBeanName();
                    if(StringUtils.isBlank(beanName)){
                        continue;
                    }
                    //SpringContextUtils.getBeansOfType(JumpConditionIntterface.class).en
                    JumpConditionIntterface dealBean =     getJumpConditionIntterfaceByName(beanName);
                    if( dealBean == null){
                        commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                                "{} 阶段跳过策略判断,符合任一条件则进入，返回fasle，因为bean不存在,{}",stage,beanName);

                        return false;
                    }
                    ret = ret ||  dealBean.isMatch(claim,conditionVO);
                    if( ret){
                        commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                                "{} 阶段跳过策略判断,符合任一条件则进入，返回true,beanName,{}",stage,beanName);

                        return ret;
                    }
                }else if(conditionVO.getType() == 1){
                    // 脚本条件
                    String scriptBeanName = "scriptCondition";
                    JumpConditionIntterface dealBean =     getJumpConditionIntterfaceByName(scriptBeanName);
                    if( dealBean == null){
                        return false;
                    }
                    ret = ret ||   dealBean.isMatch(claim,conditionVO);
                    if( ret){
                        commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                                "{} 阶段跳过策略判断,符合任一条件则进入，返回true,beanName,{}",stage,scriptBeanName);

                        return ret;
                    }
                }else{
                    return  false;
                }
            }

            commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                    "{} 阶段跳过策略判断,符合任一条件则进入，返回{} ",stage,ret);

            return ret;
        }


        throw new RuntimeException("not support jumpType :"+jumpType);
    }


    private JumpConditionIntterface getJumpConditionIntterfaceByName(String beanName){
        Map<String,JumpConditionIntterface> springBeanMap = SpringContextUtils.getBeansOfType(JumpConditionIntterface.class);
        Map<String,JumpConditionIntterface> beanMap  = new HashMap<>();
        springBeanMap.entrySet().stream().forEach(t->{
            JumpConditionIntterface bean =    t.getValue();
            beanMap.put(bean.getBeanName(),bean);
        });

        return beanMap.get(beanName);
    }

}
