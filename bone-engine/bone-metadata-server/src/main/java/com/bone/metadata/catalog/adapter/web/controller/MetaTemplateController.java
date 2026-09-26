package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.catalog.application.MetaTemplateApplicationService;
import com.bone.metadata.catalog.application.command.cmd.InstantiateFromTemplateCommand;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplate;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateField;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台模型模板 API（G3/ADR-0031）：模板目录（全租户共享只读）与「从模板实例化」。
 *
 * <p>权限（2a §4.3）：目录/字段查看 {@code metadata:template:read}（兼容 {@code metadata:read}）；实例化=建模写， {@code
 * metadata:template:write} 或 {@code metadata:model:write}（兼容 {@code metadata:write}）。
 */
@RestController
@RequestMapping("/api/v1/metadata/templates")
@RequiredArgsConstructor
public class MetaTemplateController {

  private final MetaTemplateApplicationService templateApplicationService;

  @GetMapping
  @PreAuthorize("hasAnyAuthority('metadata:template:read', 'metadata:read')")
  public ApiResponse<List<MetaModelTemplate>> list(@RequestParam(required = false) String keyword) {
    return ApiResponse.success(templateApplicationService.listTemplates(keyword));
  }

  @GetMapping("/{id}/fields")
  @PreAuthorize("hasAnyAuthority('metadata:template:read', 'metadata:read')")
  public ApiResponse<List<MetaModelTemplateField>> fields(@PathVariable Long id) {
    return ApiResponse.success(templateApplicationService.templateFields(id));
  }

  @PostMapping("/{id}/instantiate")
  @PreAuthorize(
      "hasAnyAuthority('metadata:template:write', 'metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Long>> instantiate(
      @PathVariable Long id, @RequestBody InstantiateFromTemplateCommand cmd) {
    Long entityId = templateApplicationService.instantiate(id, cmd);
    return ResponseEntity.created(URI.create("/api/v1/metadata/entities/" + entityId))
        .body(ApiResponse.success(entityId));
  }
}
