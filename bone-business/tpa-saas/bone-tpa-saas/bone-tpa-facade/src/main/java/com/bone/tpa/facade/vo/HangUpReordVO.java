package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class HangUpReordVO {
    /**
     * 赔案号
     * 入参只需要填写这个
     */
    private String claimNo;

    /**
     * 挂起时间
     */
    private String hangUpTime;
    /**
     * 用户补充说明
     */
    private String explanation;

    /**
     * 挂起原因描述
     */
    private String reason;
    /**
     * 挂起类型
     * 见枚举TpaHandupReason
     */
    private String reasonType;

}
