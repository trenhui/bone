package com.bone.tpa.sdk.vo;

import lombok.Data;

@Data
public class InputConfigVO {

    /**
     * 0 限发票层
     * 1 发票层和费用明细层(需校验)
     * 2 发票层和费用明细层(不校验)
     */
    private String invoiceDeepType="0";


    /**
     * 0 先技力再传统录入
     * 1 仅传统录入
     * 2 仅技力录入(在质检环境补充)
     */
    private String inputType="0";


    /**
     * 发票是否关联影响件
     * 0 否
     * 1 是
     */
    private String invoiceImageBind = "0";

    /**
     * 是否自动化
     * 0 否
     * 1 是
     */
    private String autoTag="0";
    /**
     * 处理人分配策略
     * 0 手工分配
     * 1 随机分配
     */
    private String dealerAssignType="0";


}
