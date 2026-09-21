package com.bone.system.infrastructure.gateway;

import com.bone.system.domain.console.ServiceStatus;
import com.bone.system.domain.gateway.ServiceHealthGateway;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;

/**
 * 占位 {@link ServiceHealthGateway} 实现（已废弃）。
 *
 * <p>仅自报本进程 Actuator {@code health()} 状态、远端恒 {@code UNKNOWN}；保留为接入服务注册中心（Eureka/Consul）时的 {@code
 * RegistryServiceHealthGateway} 参考样板。实际运行已由 {@link HttpProbeServiceHealthGatewayAdapter}（主动探活）取代。
 */
// 注意：本类不再标注 @Component，避免与 HttpProbeServiceHealthGatewayAdapter 同接口双 Bean 歧义；如需启用注册中心探活，
// 改为 @Component 并移除 HttpProbeServiceHealthGatewayAdapter 的 @Component 即可。
@RequiredArgsConstructor
public class LocalActuatorServiceHealthGatewayAdapter implements ServiceHealthGateway {

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
