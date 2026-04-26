package com.bone.tpa.claim.flow.jumpcondition;

import com.bone.tpa.api.enums.SourceType;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.vo.FlowConditionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 线下赔案
 */
@Slf4j
@Service
public class ChannelOfflineCondition implements  JumpConditionIntterface{
    @Autowired
    private CommonLogService commonLogService;
    /**
     * beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "channelOffline";
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
        boolean ret = fillRet(claim,conditionVO);
        commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                this.getBeanName()+" 赔案是线下条件，返回:{}" , ret);
        return ret;
    }


    private Boolean fillRet(Claim claim, FlowConditionVO conditionVO){
        SourceType sourceTypeEnum = SourceType.getEnumCode(claim.getSource());
        if( sourceTypeEnum == null){
            return false;
        }
        if( sourceTypeEnum == SourceType.线下){
            return true;
        }
        return false;
    }
}
