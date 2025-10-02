package com.bone.lowcode.integration.application.service;

import com.bone.lowcode.integration.application.service.impl.CamelRouteServiceImpl;
import com.bone.lowcode.integration.enums.EnvEnum;

public interface IDirectNodeManagerService {

    void processDirectNode(EnvEnum env, CamelRouteServiceImpl.FlowProcess flowProcess);

    CamelRouteServiceImpl.FlowProcess findRouteByDirectUri(EnvEnum env, String directUri);
}
