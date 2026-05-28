package com.bone.system.domain.gateway;

import com.bone.system.domain.model.console.ServiceStatus;
import java.util.List;

/**
 * 各微服务健康状态出站端口（控制台概览"服务列表"）。
 *
 * <p>实现位于 {@code infrastructure/gateway/}，当前默认 {@code LocalActuatorServiceHealthGateway}
 * 仅自报本地 Actuator 状态，远端服务给 {@code UNKNOWN}；接入 Eureka/Consul 后替换为真探测（[Target]）。
 */
public interface ServiceHealthGateway {

    /**
     * @return 平台微服务的最新状态摘要（包含本进程）。实现必须容错，绝不抛异常导致概览整体失败。
     */
    List<ServiceStatus> listServiceStatuses();
}
