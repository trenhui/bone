package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.assembler.ConfigAssembler;
import com.bone.system.adapter.web.dto.request.ConfigPageReq;
import com.bone.system.adapter.web.dto.request.CreateConfigReq;
import com.bone.system.adapter.web.dto.request.UpdateConfigReq;
import com.bone.system.adapter.web.dto.response.ConfigResp;
import com.bone.system.application.ConfigApplicationService;
import com.bone.system.application.ConfigSnapshotApplicationService;
import com.bone.system.application.command.ConfigSnapshotImportResult;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统配置控制器。
 *
 * <p><b>职责边界</b>：鉴权结果接入、协议 DTO 转换、统一响应封装——状态判断、事务与 Repository 调用一律不在这里（E-3.6 职责表）。因此所有用例统一走 {@link
 * ConfigApplicationService} 一个入口， Controller 不再同时依赖写 Handler 与读 Handler。
 */
@Tag(name = "系统配置", description = "系统配置管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/config")
@RequiredArgsConstructor
public class ConfigController {

  private final ConfigApplicationService configApplicationService;
  private final ConfigSnapshotApplicationService configSnapshotApplicationService;
  private final ConfigAssembler configAssembler;

  private static final DateTimeFormatter EXPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  @Operation(summary = "创建配置")
  @PostMapping
  @PreAuthorize("hasAuthority('sys:config:write')")
  public ApiResponse<Long> create(@Valid @RequestBody CreateConfigReq req) {
    return ApiResponse.success(configApplicationService.create(configAssembler.toCommand(req)));
  }

  @Operation(summary = "更新配置")
  @PutMapping
  @PreAuthorize("hasAuthority('sys:config:write')")
  public ApiResponse<Void> update(@Valid @RequestBody UpdateConfigReq req) {
    configApplicationService.update(configAssembler.toCommand(req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除配置")
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('sys:config:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    configApplicationService.delete(id);
    return ApiResponse.success();
  }

  @Operation(summary = "根据ID获取配置")
  @GetMapping("/{id}")
  public ApiResponse<ConfigResp> getById(@PathVariable Long id) {
    return ApiResponse.success(
        configApplicationService.getById(id).map(configAssembler::toResp).orElse(null));
  }

  @Operation(summary = "根据配置键获取配置")
  @GetMapping("/key/{key}")
  public ApiResponse<ConfigResp> getByKey(@PathVariable String key) {
    return ApiResponse.success(
        configApplicationService.getByKey(key).map(configAssembler::toResp).orElse(null));
  }

  @Operation(summary = "查询配置列表")
  @GetMapping
  public ApiResponse<PageResult<ConfigResp>> list(ConfigPageReq req) {
    return page(req);
  }

  @Operation(summary = "分页查询配置列表")
  @GetMapping("/page")
  public ApiResponse<PageResult<ConfigResp>> page(ConfigPageReq req) {
    PageResult<ConfigResp> page =
        configApplicationService.page(configAssembler.toQuery(req)).map(configAssembler::toResp);
    return ApiResponse.success(page);
  }

  @Operation(summary = "导出配置快照（JSON，加密项脱敏）")
  @PostMapping("/export")
  // 权限码取 sys:config:write 而非某个"读"码：权限目录当前只登记了 sys:config:write，
  // 没有 sys:config:read。导出的是**全量**配置快照，虽对加密项脱敏，仍是配置面的完整视图，
  // 属敏感面。在无独立读码可选时按更严一侧处理（能导= 能改），避免为它新造一个码。
  @PreAuthorize("hasAuthority('sys:config:write')")
  public ResponseEntity<byte[]> exportConfig() {
    byte[] bytes = configSnapshotApplicationService.exportSnapshot();
    String ts = LocalDateTime.now().format(EXPORT_TS);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"config-snapshot-" + ts + ".json\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(bytes);
  }

  @Operation(summary = "导入配置快照（按 configKey upsert）")
  @PostMapping("/import")
  // 导入即写（按 configKey upsert），与同控制器的 create/update/delete 同码。
  @PreAuthorize("hasAuthority('sys:config:write')")
  public ApiResponse<ConfigSnapshotImportResult> importConfig(
      @RequestParam("file") MultipartFile file) {
    try {
      String json = new String(file.getBytes(), StandardCharsets.UTF_8);
      return ApiResponse.success(configSnapshotApplicationService.importSnapshot(json));
    } catch (IOException e) {
      throw SystemErrors.of(SystemErrorCodes.CONFIG_SNAPSHOT_READ_FAILED, e.getMessage());
    }
  }

  @Operation(summary = "配置变更历史")
  @GetMapping("/{id}/history")
  public ApiResponse<List<Map<String, Object>>> history(@PathVariable Long id) {
    return ApiResponse.success(configApplicationService.history(id));
  }
}
