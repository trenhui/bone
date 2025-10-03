package com.bone.integration.application.service;

public interface ICamelRouteService {

    /**
     * 根据流程ID生成camel路由
     * @param flowId 流程ID
     */
    void buildRouteByFlowId(Long flowId, String env);

    void buildRouteByFlowVersionId(Long flowVersionId);

    void stopRouteByFlowVersionId(Long flowVersionId);

    void buildAllCamelFlow();
}
