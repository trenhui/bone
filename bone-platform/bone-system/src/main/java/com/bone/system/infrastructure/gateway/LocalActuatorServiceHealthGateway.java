package com.bone.system.infrastructure.gateway;

import com.bone.system.domain.gateway.ServiceHealthGateway;
import com.bone.system.domain.model.console.ServiceStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Component;

/**
 * 默认 {@link ServiceHealthGateway} 实现。
 *
 * <p>仅自报本进程 Actuator {@code health()} 状态；远端服务返回 {@code UNKNOWN}，避免误导用户。 接入服务注册中心（Eureka/Consul）或
 * Gateway 主动探活后替换为 {@code RegistryServiceHealthGateway}（[Target]）。
 */
@Component
@RequiredArgsConstructor
public class LocalActuatorServiceHealthGateway implements ServiceHealthGateway {

  private static final String UNKNOWN = "UNKNOWN";

  private final HealthEndpoint healthEndpoint;

  @Override
  public List<ServiceStatus> listServiceStatuses() {
    String localStatus = safeLocalStatus();
    List<ServiceStatus> list = new ArrayList<>();
    list.add(svc("IAM", "bone-iam", "8081", UNKNOWN));
    list.add(svc("元数据", "bone-metadata-server", "9001", UNKNOWN));
    list.add(svc("主数据", "bone-masterdata", "8080", UNKNOWN));
    list.add(svc("集成", "bone-integration", "8085", UNKNOWN));
    list.add(svc("系统管理", "bone-system", "8083", localStatus));
    list.add(svc("扩展 Studio", "bone-extension-studio", "8088", UNKNOWN));
    return list;
  }

  private String safeLocalStatus() {
    try {
      return healthEndpoint.health().getStatus().getCode();
    } catch (Exception e) {
      return UNKNOWN;
    }
  }

  private static ServiceStatus svc(String name, String code, String port, String status) {
    return ServiceStatus.builder()
        .name(name)
        .serviceCode(code)
        .port(port)
        .status(status)
        .latencyMs(0)
        .build();
  }
}
