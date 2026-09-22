package com.bone.system.application;

import com.bone.system.domain.gateway.KeyMetricsGateway;
import com.bone.system.domain.gateway.ResourceUsageGateway;
import com.bone.system.domain.gateway.ServiceHealthGateway;
import com.bone.system.domain.model.console.ConsoleOverview;
import com.bone.system.domain.model.console.KeyMetrics;
import com.bone.system.domain.model.console.QuickAction;
import com.bone.system.domain.model.console.ResourceUsage;
import com.bone.system.domain.model.console.ServiceStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 控制台概览用例入口（读侧）。
 *
 * <p><b>这里没有 QueryPort，也没有 repository</b>：控制台数据不属于任何一个聚合——它是三类外部事实 （服务健康 / 资源占用 /
 * 关键指标）的组合。这是典型的「跨上下文只读组合」，由应用用例直接编排 {@code domain/gateway/*} 出站端口（E-4.3：领域规则需要的外部业务事实才进 {@code
 * domain/gateway}）。
 *
 * <p><b>为什么可以直接把 {@code ConsoleOverview} 交出去</b>：它是 {@code @Value} 的不可变读侧值对象，不是聚合——没有 {@code save}
 * 入口，也没有 setter 可达的状态迁移。 E-4.2 禁止的是把<em>可变聚合</em>暴露到 adapter（序列化聚合等于开放状态迁移接口），此处不适用。
 *
 * <p><b>MeterRegistry 为何出现在应用层</b>：{@code bone_console_overview_refresh_total}
 * 度量的是「这个用例被调用了多少次」，语义归属用例而非某个基础设施组件；放在 gateway 侧会让每个实现 各记一份、口径不一致。命名与 PromQL 真源 {@code
 * Bone-可观测性规范.md} §4.2.1 对齐。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsoleApplicationService {

  private final ServiceHealthGateway serviceHealthGateway;
  private final ResourceUsageGateway resourceUsageGateway;
  private final KeyMetricsGateway keyMetricsGateway;
  private final MeterRegistry meterRegistry;

  /** 聚合概览：服务 + 资源 + 关键指标一次性取齐。 */
  public ConsoleOverview overview() {
    meterRegistry.counter("bone_console_overview_refresh_total").increment();
    return ConsoleOverview.builder()
        .services(serviceHealthGateway.listServiceStatuses())
        .resourceUsage(resourceUsageGateway.snapshot())
        .keyMetrics(keyMetricsGateway.collect())
        .alerts(List.of())
        .updatedAt(Instant.now())
        .build();
  }

  public List<ServiceStatus> serviceStatuses() {
    return serviceHealthGateway.listServiceStatuses();
  }

  public ResourceUsage resourceUsage() {
    return resourceUsageGateway.snapshot();
  }

  public KeyMetrics keyMetrics() {
    return keyMetricsGateway.collect();
  }

  /**
   * 控制台快捷入口（与 {@code bone-shell} 微应用路由一致）。
   *
   * <p>当前为静态配置：这组数据是路由清单而非业务事实，落库只会多一次查询。接入 {@code cnsl_quick_action} 表后按租户 / 角色筛选时，这里再改为读端口。
   */
  public List<QuickAction> quickActions() {
    return List.of(
        quick("iam", "账号权限管理", "/iam", "UserOutlined"),
        quick("metadata", "元数据管理", "/metadata", "DatabaseOutlined"),
        quick("masterdata", "主数据管理", "/masterdata", "DatabaseOutlined"),
        quick("integration", "集成管理", "/integration", "LinkOutlined"),
        quick("system", "系统管理", "/system", "SettingOutlined"),
        quick("extension", "扩展管理", "/extension", "AppstoreOutlined"));
  }

  private static QuickAction quick(String id, String title, String path, String icon) {
    return QuickAction.builder().id(id).title(title).path(path).icon(icon).build();
  }
}
