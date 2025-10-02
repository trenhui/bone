package com.bone.lowcode.integration.application.service.impl;

import com.bone.lowcode.integration.application.service.ICamelRouteService;
import com.bone.lowcode.integration.application.service.IDeployWorkerService;
import com.bone.lowcode.integration.enums.EnvEnum;
import com.bone.lowcode.integration.uitls.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.bone.lowcode.integration.common.Constants.REDIS_WORKER_KEY;

@Component
@Slf4j
public class DeployWorkerImpl implements IDeployWorkerService {
    @Resource
    private ICamelRouteService iCamelRouteService;
    private final String localIp = IpUtils.getLocalIp();
    @Value("${server.port}")
    private String port;
    @Value("${camel.cluster.id}")
    private String clusterId;
    private String protocol = "http";
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void init() {
        sendHeartbeat();
        scheduledExecutor.scheduleAtFixedRate(this::sendHeartbeat, 5, 10, TimeUnit.SECONDS);
    }

    @Override
    public void deployRouteDev(String deployId, Long flowId) {
        // todo: 这里应该是异步部署
        iCamelRouteService.buildRouteByFlowId(flowId, EnvEnum.DEV.name());
    }

    @Override
    public void deployRouteProd(String deployId, Long flowVersionId) {
        iCamelRouteService.buildRouteByFlowVersionId(flowVersionId);
    }

    @Override
    public void stopRouteDev(String deployId, Long flowId) {
    }

    @Override
    public void stopRouteProd(String deployId, Long flowVersionId) {
        iCamelRouteService.stopRouteByFlowVersionId(flowVersionId);
    }

    @Override
    public void sendHeartbeat() {
        try {
            String key = protocol + ":" + localIp + ":" + port;
            String value = String.valueOf(System.currentTimeMillis());
            redisTemplate.opsForHash().put(REDIS_WORKER_KEY + ":" + clusterId, key, value);
            log.info("sendHeartbeat: {} - {}", key, value);
        } catch (Exception e) {
            log.error("sendHeartbeat error", e);
        }
    }
}
