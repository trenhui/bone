package com.bone.tpa.claim.application.request;

import com.bone.tpa.sdk.vo.InputConfigVO;
import com.bone.tpa.sdk.vo.PreCheckConfigVO;
import lombok.Data;

@Data
public class SaveInputConfigRequest {

    private String bizIdentityCode;
    private InputConfigVO config;

}
