package com.bone.lowcode.integration.application.service;

import com.bone.lowcode.integration.application.service.impl.CamelRouteServiceImpl;
import com.bone.lowcode.integration.enums.EnvEnum;

public interface IRouteManageService {

    void addRoute(EnvEnum env, CamelRouteServiceImpl.FlowProcess flowProcess) throws Exception;

    void startRoute(String routeId);

    void stopRoute(String routeId);

    void removeRoute(String routeId);

    void findRoute(String routeId);
}
