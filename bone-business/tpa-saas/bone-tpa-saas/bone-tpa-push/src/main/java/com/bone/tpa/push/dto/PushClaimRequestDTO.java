package com.bone.tpa.push.dto;

import lombok.Data;

@Data
public class PushClaimRequestDTO {
    private ClaimDetailPushDTO claimPushInfo;
    private String insuranceName;
    private String branchName;
    private String insureName;
    private String pushType;
    private Long pushTime;
}
