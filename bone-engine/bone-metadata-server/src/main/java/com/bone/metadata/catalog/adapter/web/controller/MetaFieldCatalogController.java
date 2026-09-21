package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.MetaEntityApplicationService;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaFieldCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaFieldCommand;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.qry.MetaFieldPageQuery;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 元数据字段建模 REST 接口（嵌套于实体资源下）。入站边界仅依赖 {@link MetaEntityApplicationService}（ADR-0028）。 */
@RestController
@RequestMapping("/api/v1/metadata/entities/{entityId}/fields")
@RequiredArgsConstructor
@Tag(name = "元数据字段建模", description = "字段 CRUD")
public class MetaFieldCatalogController {

  private final MetaEntityApplicationService metaEntityApplicationService;

  @PostMapping
  @Operation(summary = "创建字段", description = "为指定实体新增字段")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Long>> create(
      @PathVariable("entityId") Long entityId, @Valid @RequestBody CreateMetaFieldCommand cmd) {
    cmd.setEntityId(entityId);
    Long newId = metaEntityApplicationService.createField(entityId, cmd);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag("\"v1\"")
        .body(ApiResponse.success(newId));
  }

  @GetMapping("/{fieldId}")
  @Operation(summary = "获取字段详情", description = "根据字段 ID 查询详情")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<MetaFieldDTO> detail(
      @PathVariable("entityId") Long entityId, @PathVariable("fieldId") Long fieldId) {
    return ApiResponse.success(metaEntityApplicationService.getField(entityId, fieldId));
  }

  @PutMapping("/{fieldId}")
  @Operation(summary = "更新字段", description = "更新字段元数据（含乐观锁）")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Void>> update(
      @PathVariable("entityId") Long entityId,
      @PathVariable("fieldId") Long fieldId,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody UpdateMetaFieldCommand cmd) {
    Integer version = CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null);
    Integer newVersion = metaEntityApplicationService.updateField(fieldId, cmd, version);
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(newVersion))
        .body(ApiResponse.success());
  }

  @DeleteMapping("/{fieldId}")
  @Operation(summary = "删除字段", description = "删除字段（草稿态）")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> delete(
      @PathVariable("entityId") Long entityId, @PathVariable("fieldId") Long fieldId) {
    metaEntityApplicationService.deleteField(fieldId);
    return ApiResponse.success();
  }

  @GetMapping
  @Operation(summary = "分页查询字段", description = "按实体/关键字分页查询字段")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<PageResult<MetaFieldDTO>> page(
      @PathVariable("entityId") Long entityId, MetaFieldPageQuery qry) {
    return ApiResponse.success(
        metaEntityApplicationService.pageFields(
            entityId, qry.getKeyword(), qry.getPageNum(), qry.getPageSize()));
  }
}
