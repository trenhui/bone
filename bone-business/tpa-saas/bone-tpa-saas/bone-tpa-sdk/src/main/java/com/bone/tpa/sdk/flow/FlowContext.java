package com.bone.tpa.sdk.flow;

import com.bone.tpa.sdk.claim.model.Claim;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FlowContext {

    private Claim claim;
    private Map<String,Object> params;
}
