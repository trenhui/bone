package com.bone.tpa.claim.application.request;

import com.bone.tpa.sdk.vo.PreCheckConfigVO;
import lombok.Data;

@Data
public class SavePreCheckConfigRequest {
    private String bizIdentityCode;
    private PreCheckConfigVO config;
}
