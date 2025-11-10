package com.bone.integration.flow.visitor.camel.context;

import com.bone.integration.enums.EnvEnum;
import com.bone.integration.utils.RouteUtils;
import lombok.Data;

@Data
public class FlowContext {
    private boolean supportMultiVersion;
    private String version;
    private EnvEnum env;
    private String flowKey;
    private String appCode;

    public String getRouteId() {
        return RouteUtils.getRouteId(appCode, flowKey, version, supportMultiVersion);
    }
}
