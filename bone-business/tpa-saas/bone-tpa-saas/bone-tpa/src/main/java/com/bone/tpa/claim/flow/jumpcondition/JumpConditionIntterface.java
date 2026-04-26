package com.bone.tpa.claim.flow.jumpcondition;

import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.vo.FlowConditionVO;
import org.springframework.beans.factory.annotation.Autowired;

public interface JumpConditionIntterface {


    /**
     * beanName
     * @return
     */
    String getBeanName();

    /**
     * 是否命中
     * @param claim
     * @return
     */
    boolean isMatch(Claim claim, FlowConditionVO conditionVO);
}
