package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.application.query.handler.ConsoleOverviewQueryHandler;
import com.bone.system.application.query.handler.QuickActionsQueryHandler;
import com.bone.system.domain.model.console.ConsoleOverview;
import com.bone.system.domain.model.console.KeyMetrics;
import com.bone.system.domain.model.console.QuickAction;
import com.bone.system.domain.model.console.ResourceUsage;
import com.bone.system.domain.model.console.ServiceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制台与仪表盘聚合接口（详设 §5.1 As-Is 单一真源）。
 *
 * <p><b>权限契约</b>：所有端点要求 {@code sys:console:read}（详设 §5.0 / §5.1）。 自 v2.0.1 起方法级
 * {@code @PreAuthorize} 已启用，由 {@code SecurityConfig + JwtAuthenticationFilter} 解析 IAM 颁发的 JWT
 * 后强制校验。
 *
 * <p>所有数据通过 {@code application.query.handler.*} 编排 {@code domain.gateway.*} 出站端口获取，遵循 CQRS
 * Handler-only 架构（《Bone-DDD》§14 / §20）。
 */
@Tag(name = "控制台", description = "系统概览、服务状态、资源使用、关键指标、快捷操作")
@RestController
@RequestMapping(PlatformApiPaths.CONSOLE_V1)
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('sys:console:read')")
public class ConsoleController {

  private final ConsoleOverviewQueryHandler consoleOverviewQueryHandler;
  private final QuickActionsQueryHandler quickActionsQueryHandler;

  @Operation(
      summary = "获取系统概览（聚合服务、资源、关键指标）",
      description = "权限码 `sys:console:read`。前端 `bone-shell` 默认 30s 轮询。")
  @GetMapping("/overview")
  public ApiResponse<ConsoleOverview> overview() {
    return ApiResponse.success(consoleOverviewQueryHandler.handle());
  }

  @Operation(
      summary = "获取各微服务状态摘要",
      description =
          "权限码 `sys:console:read`。当前仅本进程 Actuator 真实，远端为 `UNKNOWN`（[Target] 接入服务注册中心后真探测）。")
  @GetMapping("/services")
  public ApiResponse<List<ServiceStatus>> services() {
    return ApiResponse.success(consoleOverviewQueryHandler.handleServices());
  }

  @Operation(
      summary = "获取节点资源使用情况（JVM）",
      description = "权限码 `sys:console:read`。CPU/磁盘字段为 [Target] 占位。")
  @GetMapping("/resources")
  public ApiResponse<ResourceUsage> resources() {
    return ApiResponse.success(consoleOverviewQueryHandler.handleResources());
  }

  @Operation(
      summary = "获取关键业务指标",
      description = "权限码 `sys:console:read`。按 DDL 表存在性做最佳努力 COUNT；单表失败容错为 0。")
  @GetMapping("/metrics")
  public ApiResponse<KeyMetrics> metrics() {
    return ApiResponse.success(consoleOverviewQueryHandler.handleMetrics());
  }

  @Operation(
      summary = "获取快速操作列表（与 bone-shell 微应用路由对齐）",
      description = "权限码 `sys:console:read`。当前为静态配置，[Target] 后改为 `cnsl_quick_action` 表驱动。")
  @GetMapping("/quick-actions")
  public ApiResponse<List<QuickAction>> quickActions() {
    return ApiResponse.success(quickActionsQueryHandler.handle());
  }
}
