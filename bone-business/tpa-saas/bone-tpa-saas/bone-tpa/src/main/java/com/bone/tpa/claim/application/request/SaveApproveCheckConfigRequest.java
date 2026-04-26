package com.bone.tpa.claim.application.request;

import com.bone.tpa.sdk.vo.ApproveCheckConfigVO;
import lombok.Data;

@Data
public class SaveApproveCheckConfigRequest {


    private String bizIdentityCode;
    private ApproveCheckConfigVO config;
}
