package com.bone.tpa.claim.application.request;

import com.bone.tpa.sdk.vo.ApproveConfigVO;
import com.bone.tpa.sdk.vo.InputConfigVO;
import lombok.Data;

@Data
public class SaveApproveConfigRequest {
    private String bizIdentityCode;
    private ApproveConfigVO config;
}
