package com.bone.tpa.claim.flow.jumpcondition;

import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.flow.jumpcondition.param.ScriptConditionContext;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.util.ScriptExcuteUtil;
import com.bone.tpa.sdk.vo.FlowConditionVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ScriptCondition implements  JumpConditionIntterface{
    @Autowired
    private CommonLogService commonLogService;
    /**
     * beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "scriptCondition";
    }

    /**
     * 是否命中
     *
     * @param claim
     * @param conditionVO
     * @return
     */
    @Override
    public boolean isMatch(Claim claim, FlowConditionVO conditionVO) {
        if(StringUtils.isBlank(conditionVO.getScript())){
            return false;
        }
        ScriptConditionContext context = new ScriptConditionContext();
        context.setClaim(claim);
        //设置beanMap，后续可以再脚本里执行
        Map<String,JumpConditionIntterface> springBeanMap = SpringContextUtils.getBeansOfType(JumpConditionIntterface.class);
        Map<String,JumpConditionIntterface> beanMap  = new HashMap<>();
        springBeanMap.entrySet().stream().forEach(t->{
            JumpConditionIntterface bean =    t.getValue();
            if(bean.getBeanName().equals(this.getBeanName())){
               return;
            }
            beanMap.put(bean.getBeanName(),bean);
        });
        context.setConditionMap(beanMap);
        context.setCommonLogService(commonLogService);
        Boolean ret =false;
        try {
              ret = (Boolean) ScriptExcuteUtil.runScirpt(conditionVO.getScript(),"execute",context);
            commonLogService.addLogASync(claim.getId().toString(),
                    CommonLogType.FLOW_LOG,
                    " {} ,脚本执行返回:{}",conditionVO.getScript(),ret);
        } catch (Throwable e) {
           log.info("脚本执行异常",e);
           commonLogService.addLogASync(claim.getId().toString(),
                   CommonLogType.FLOW_LOG,
                   "{}     ,脚本执行异常:{}",conditionVO.getScript(),e.getMessage());
        }finally {
            return ret;
        }
    }
}
