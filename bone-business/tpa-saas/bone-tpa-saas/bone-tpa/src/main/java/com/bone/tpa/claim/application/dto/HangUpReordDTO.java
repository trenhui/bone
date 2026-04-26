package com.bone.tpa.claim.application.dto;

import lombok.Data;

/**
 * tpa挂起记录
 */
@Data
public class HangUpReordDTO {
    private String hangUpTime;

    private String claimNo;
    private String explanation;//用户补充说明

    private String reason;
    private String reasonType;//见枚举TpaHandupReason

}
