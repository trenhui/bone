package com.bone.tpa.sdk.vo;

import lombok.Data;

@Data
public class ApproveCheckConfigVO {

    /**
     * 处理人分配策略
     * 0 手工分配
     * 1 随机分配
     */
    //// TODO: 2025/11/18
    private String dealerAssignType="0";

}
