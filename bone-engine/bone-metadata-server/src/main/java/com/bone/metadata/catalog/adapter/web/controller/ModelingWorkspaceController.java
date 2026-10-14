package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.catalog.application.ModelingWorkspaceApplicationService;
import com.bone.metadata.catalog.application.query.dto.ModelingWorkspaceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 建模工作台（模型设计页）聚合 API（P0-1）。
 *
 * <p>存在理由：该页原有取数路径为 {@code listMine×2 + listByApp×N + entities page×3 串行}， 首屏 RTT 随应用数线性增长。 本端点把统计
 * + 待发布草稿 + 应用→模块结构收敛为一次请求。
 *
 * <p>入站边界仅依赖 {@link ModelingWorkspaceApplicationService}（ADR-0028）。 读权限与实体列表一致（{@code
 * metadata:model:read}）：聚合内容以建模统计为主体； 应用列表本体（含 myRole）仍由 IAM {@code /apps/mine} 提供，无需 metadata
 * 权限——两者关测点不同，不合并（见 DTO 注释）。
 */
@RestController
@RequestMapping("/api/v1/metadata/modeling")
@RequiredArgsConstructor
public class ModelingWorkspaceController {

  private final ModelingWorkspaceApplicationService modelingWorkspaceApplicationService;

  @GetMapping("/workspace")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<ModelingWorkspaceDTO> workspace() {
    return ApiResponse.success(modelingWorkspaceApplicationService.loadWorkspace());
  }
}
