package com.bone.tpa.sdk.flow;


import com.bone.tpa.sdk.vo.FlowConditionVO;

public interface FlowJumpCondition {


    /**
     * 注册的beanName
     * @return
     */
    String getBeanName();

    /**
     * 返回计算结果
     * @param context
     * @return
     */
    Boolean excute(FlowConditionVO config, FlowContext context);

}
