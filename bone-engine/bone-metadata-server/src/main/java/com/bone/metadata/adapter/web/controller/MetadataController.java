package com.bone.metadata.adapter.web.controller;

import com.bone.core.web.PlatformApiPaths;
import com.bone.metadata.catalog.application.idempotency.CatalogIdempotencyService;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.metadata.client.FieldsByNamesRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 元数据服务 REST 控制器
 *
 * <p>1. POST /api/v1/metadata/fields:search — 复合查询 2. POST /api/v1/metadata/fields:searchByNames 3.
 * POST /api/v1/metadata/fields:allocate — 批量创建 4. GET /api/v1/metadata/health — 健康检查（公开）
 */
@RestController
@RequestMapping(PlatformApiPaths.METADATA_V1)
@RequiredArgsConstructor
@Tag(name = "元数据管理", description = "扩展字段全生命周期管理")
public class MetadataController {

  private static final String ALLOCATE_PATH = PlatformApiPaths.METADATA_V1 + "/fields:allocate";

  private final MetadataService metadataService;
  private final CatalogIdempotencyService catalogIdempotencyService;
  private final ObjectMapper objectMapper;

  /** 复合查询扩展字段 POST /api/v1/metadata/fields:search */
  @PostMapping("/fields:search")
  @Operation(
      summary = "多条件组合查询扩展字段",
      description = "名称、数据类型等）的扩展字段查询",
      security = @SecurityRequirement(name = "BearerAuth"),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content = @Content(schema = @Schema(implementation = FieldMetadata.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "429", description = "请求过于频繁"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
      })
  @PreAuthorize("hasAnyAuthority('metadata:read')")
  public com.bone.core.model.ApiResponse<List<FieldMetadata>> searchFields(
      @Parameter(
              description = "查询条件实体",
              required = true,
              content = @Content(schema = @Schema(implementation = AllocationContext.class)))
          @Valid
          @RequestBody
          AllocationContext ctx) {
    List<FieldMetadata> result = metadataService.findExtensionFields(ctx);
    return com.bone.core.model.ApiResponse.success(result);
  }

  /** POST /api/v1/metadata/fields:searchByNames */
  @PostMapping("/fields:searchByNames")
  @Operation(
      summary = "按名称列表查询扩展字段",
      description = "根据上下文和字段名称列表批量查询扩展字段",
      security = @SecurityRequirement(name = "BearerAuth"),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = FieldMetadata.class)))),
        @ApiResponse(responseCode = "400", description = "请求参数无效"),
        @ApiResponse(responseCode = "404", description = "未找到匹配字段")
      })
  @PreAuthorize("hasAnyAuthority('metadata:read')")
  public com.bone.core.model.ApiResponse<List<FieldMetadata>> searchFieldsByNames(
      @Parameter(
              description = "查询条件实体",
              required = true,
              content = @Content(schema = @Schema(implementation = FieldsByNamesRequest.class)))
          @Valid
          @RequestBody
          FieldsByNamesRequest request) {
    AllocationContext ctx = request.getContext();
    List<String> names = request.getLogicalNames();
    List<FieldMetadata> result = metadataService.findExtensionFieldsByNames(ctx, names);
    return com.bone.core.model.ApiResponse.success(result);
  }

  /** POST /api/v1/metadata/fields:allocate */
  @PostMapping("/fields:allocate")
  @Operation(
      summary = "批量创建扩展字段",
      description = "为指定上下文批量创建新字段，自动分配物理存储并持久化",
      security = @SecurityRequirement(name = "BearerAuth"),
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "创建成功",
            content =
                @Content(
                    array = @ArraySchema(schema = @Schema(implementation = FieldMetadata.class)))),
        @ApiResponse(responseCode = "400", description = "请求参数无效"),
        @ApiResponse(responseCode = "401", description = "未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "409", description = "字段已存在"),
        @ApiResponse(responseCode = "422", description = "字段验证失败"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
      })
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<com.bone.core.model.ApiResponse<List<FieldMetadata>>>
      allocateAndPersistFields(
          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
          @Parameter(
                  description = "批量创建字段请求实体列表",
                  required = true,
                  content =
                      @Content(
                          array =
                              @ArraySchema(schema = @Schema(implementation = FieldMetadata.class))))
              @Valid
              @RequestBody
              List<FieldMetadata> toCreate)
          throws JsonProcessingException {
    String fingerprint =
        CatalogIdempotencyService.fingerprint(objectMapper.writeValueAsString(toCreate));
    var bodyType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(
                com.bone.core.model.ApiResponse.class,
                objectMapper
                    .getTypeFactory()
                    .constructCollectionType(List.class, FieldMetadata.class));
    Optional<ResponseEntity<com.bone.core.model.ApiResponse<List<FieldMetadata>>>> replay =
        catalogIdempotencyService.replayRawBody(
            idempotencyKey, "POST", ALLOCATE_PATH, fingerprint, bodyType);
    if (replay.isPresent()) {
      return replay.get();
    }
    List<FieldMetadata> created = metadataService.allocateAndPersistFields(toCreate);
    ResponseEntity<com.bone.core.model.ApiResponse<List<FieldMetadata>>> response =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(com.bone.core.model.ApiResponse.success(created));
    catalogIdempotencyService.rememberRawBody(
        idempotencyKey, "POST", ALLOCATE_PATH, fingerprint, response);
    return response;
  }

  /** GET /api/v1/metadata/health */
  @GetMapping("/health")
  @Operation(
      summary = "服务健康检查",
      description = "返回当前服务健康状态（200 或 503）",
      tags = {"系统监控"})
  public ResponseEntity<Void> healthCheck() {
    boolean ok = metadataService.isHealthy();
    return ok
        ? ResponseEntity.ok().build()
        : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }
}
