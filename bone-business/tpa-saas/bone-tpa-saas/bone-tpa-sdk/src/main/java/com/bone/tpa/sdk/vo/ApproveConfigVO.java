package com.bone.tpa.sdk.vo;

import lombok.Data;

@Data
public class ApproveConfigVO {
    /**
     * 发票录入类型
     * 0 限发票层
     * 1 发票层和费用明细层(需校验)
     * 2 发票层和费用明细层(不校验)
     */
    private String invoiceDeepType="0";


    /**
     * 同人同保单理算方式 0 审核环境逐案人工理算 1 审核前逐案理算 2 审核前批量逐案自动理算
     */
    //// TODO
    public String onePersonOnePolicyTag="0";

    /**
     * 是否支持多保单理算
     * 0 不支持
     * 1 支持
     *
     */
    //// TODO
    public String policyMoreCalTag = "0";

    /**
     * 是否支持修改赔付金额
     * 0 不支持
     * 1 支持
     */
    //// TODO
    public String canModifyMoneyTag="0";

    /**
     * 是否支持快捷理算
     * 0 不支持
     * 1 支持
     */
    //// TODO
    private String quickCallTag = "0";

    /**
     * 自动化审核标记
     * 0 不支持
     * 1 支持
     */
    //// TODO
    private String autoApproveTag = "0";


    /**
     * 处理人分配策略
     * 0 手工分配
     * 1 随机分配
     */
    private String dealerAssignType="0";

}
