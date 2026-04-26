package com.bone.tpa.sdk.vo;

import lombok.Data;

@Data
public class FlowConfigVO {
    /**
     * 初审配置
     */
    private BaseFlowNodeVO preCheckFlowNode = new BaseFlowNodeVO();
    /**
     * 录入配置
     */
    private  BaseFlowNodeVO inputFlowNode = new BaseFlowNodeVO();
    /**
     * 质检配置
     */
    private BaseFlowNodeVO qualityFlowNode = new BaseFlowNodeVO();
    /**
     * 审核配置
     */
    private BaseFlowNodeVO approveFlowNode = new BaseFlowNodeVO();
    /**
     * 复核配置
     */
    private BaseFlowNodeVO approveCheckFlowNode= new BaseFlowNodeVO();


}
