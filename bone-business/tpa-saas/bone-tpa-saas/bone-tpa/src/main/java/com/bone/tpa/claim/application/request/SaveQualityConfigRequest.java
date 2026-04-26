package com.bone.tpa.claim.application.request;

import com.bone.tpa.sdk.vo.InputConfigVO;
import com.bone.tpa.sdk.vo.QualityConfigVO;
import lombok.Data;

@Data
public class SaveQualityConfigRequest {
    private String bizIdentityCode;
    private QualityConfigVO config;

}
