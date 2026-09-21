package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.catalog.application.MetaEntityApplicationService;
import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物理结构治理接口（破坏性，管理员专属）。入站边界仅依赖 {@link MetaEntityApplicationService}（ADR-0028）。
 *
 * <p>权限 scope：{@code metadata:write}（local profile 授予全权）。端点仅对 RUNTIME 实体生效；在用列 / 保留列会被实现层拒绝。
 */
@RestController
@RequestMapping("/api/v1/metadata/entities/{entityId}/structure")
@RequiredArgsConstructor
public class PhysicalStructureController {

  private final MetaEntityApplicationService metaEntityApplicationService;

  /** 删除单个孤儿物理列（破坏性）。在用列 / 保留列会被拒绝并返回 REFUSED。 */
  @PostMapping("/drop-column")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<PhysicalStructurePlan> dropColumn(
      @PathVariable Long entityId, @RequestBody DropColumnRequest req) {
    return ApiResponse.success(metaEntityApplicationService.dropColumn(entityId, req.fieldCode()));
  }

  /** 清理物理表中所有不再被模型引用的孤儿列（破坏性）。 */
  @PostMapping("/drop-drifted")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<PhysicalStructurePlan> dropDrifted(@PathVariable Long entityId) {
    return ApiResponse.success(metaEntityApplicationService.dropDriftedColumns(entityId));
  }

  public record DropColumnRequest(@NotBlank String fieldCode) {}
}
