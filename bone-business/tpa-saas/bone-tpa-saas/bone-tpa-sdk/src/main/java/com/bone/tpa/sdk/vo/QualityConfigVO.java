package com.bone.tpa.sdk.vo;

import lombok.Data;

@Data
public class QualityConfigVO {
    /**
     * 0 限发票层
     * 1 发票层和费用明细层(需校验)
     * 2 发票层和费用明细层(不校验)
     */
    //// TODO: 2025/11/18  
    private String invoiceDeepType="0";
    /**
     * 发票是否关联影响件
     * 0 否
     * 1 是
     */
    private String invoiceImageBind = "0";
    /**
     * 0 不支持
     * 1 选保单不选责任
     * 2 选保单和责任
     */
    //// TODO
    private String liabilityBindType = "0";
    /**
     * 处理人分配策略
     * 0 手工分配
     * 1 随机分配
     */
    private String dealerAssignType="0";
}
