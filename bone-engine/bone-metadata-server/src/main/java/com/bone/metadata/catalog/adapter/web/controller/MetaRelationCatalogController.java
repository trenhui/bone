package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.MetaRelationApplicationService;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaRelationCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaRelationCommand;
import com.bone.metadata.catalog.application.query.dto.MetaRelationDTO;
import com.bone.metadata.catalog.application.query.qry.MetaRelationPageQuery;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 元数据关系建模 REST 接口。入站边界仅依赖 {@link MetaRelationApplicationService}（ADR-0028）。 */
@RestController
@RequestMapping("/api/v1/metadata/relationships")
@RequiredArgsConstructor
@Tag(name = "元数据关系建模", description = "实体间关系 CRUD")
public class MetaRelationCatalogController {

  private final MetaRelationApplicationService metaRelationApplicationService;

  @PostMapping
  @Operation(summary = "创建关系", description = "创建实体间关系")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Long>> create(
      @Valid @RequestBody CreateMetaRelationCommand cmd) {
    Long newId = metaRelationApplicationService.createRelation(cmd);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag("\"v1\"")
        .body(ApiResponse.success(newId));
  }

  @GetMapping("/{id}")
  @Operation(summary = "获取关系详情", description = "根据关系 ID 查询详情")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<MetaRelationDTO> detail(@PathVariable("id") Long id) {
    return ApiResponse.success(metaRelationApplicationService.getRelation(id));
  }

  @PutMapping("/{id}")
  @Operation(summary = "更新关系", description = "更新关系元数据（含乐观锁）")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Void>> update(
      @PathVariable("id") Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody UpdateMetaRelationCommand cmd) {
    Integer version = CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null);
    Integer newVersion = metaRelationApplicationService.updateRelation(id, cmd, version);
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(newVersion))
        .body(ApiResponse.success());
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除关系", description = "删除关系（草稿态）")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<Void> delete(@PathVariable("id") Long id) {
    metaRelationApplicationService.deleteRelation(id);
    return ApiResponse.success();
  }

  @GetMapping
  @Operation(summary = "分页查询关系", description = "按源/目标实体或关键字分页查询关系")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<PageResult<MetaRelationDTO>> page(MetaRelationPageQuery qry) {
    return ApiResponse.success(
        metaRelationApplicationService.pageRelations(
            qry.getSourceEntityId(),
            qry.getTargetEntityId(),
            qry.getKeyword(),
            qry.getPageNum(),
            qry.getPageSize()));
  }
}
