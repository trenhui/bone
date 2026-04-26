package com.bone.tpa.push.bean;

import com.bone.tpa.sdk.adjustment.model.Coverage;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class BatchDataBean {
    private Map<String, LiabilityConfig> liabilityMap = new HashMap<>();
    private Map<Long, Coverage> coverageMap = new HashMap<>();
}
