package com.bone.integration.application.service;

public interface IDeployWorkerService {

    void deployRouteDev(String deployId, Long flowId);

    void deployRouteProd(String deployId, Long flowVersionId);

    void stopRouteDev(String deployId, Long flowId);

    void stopRouteProd(String deployId, Long flowVersionId);

    /**
     * 发送master心跳
     */
    void sendHeartbeat();
}
