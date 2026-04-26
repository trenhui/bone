package com.bone.tpa.claim.application.request;

import com.bone.tpa.sdk.vo.FlowConfigVO;
import lombok.Data;

@Data
public class SaveFlowConfigVORequest {
    private String bizIdentityCode ;
    private FlowConfigVO config;
}
