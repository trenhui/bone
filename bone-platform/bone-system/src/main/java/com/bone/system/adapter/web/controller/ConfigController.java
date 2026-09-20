package com.bone.system.adapter.web.controller;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.converter.ConfigWebConverter;
import com.bone.system.adapter.web.dto.request.ConfigPageReq;
import com.bone.system.adapter.web.dto.request.CreateConfigReq;
import com.bone.system.adapter.web.dto.request.UpdateConfigReq;
import com.bone.system.adapter.web.dto.response.ConfigResp;
import com.bone.system.application.command.handler.ConfigCommandHandler;
import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.application.query.handler.ConfigQueryHandler;
import com.bone.system.application.service.ConfigSnapshotApplicationService;
import com.bone.system.application.service.dto.ConfigSnapshotImportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 系统配置控制器 */
@Tag(name = "系统配置", description = "系统配置管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/config")
@RequiredArgsConstructor
public class ConfigController {

  private final ConfigCommandHandler configCommandHandler;
  private final ConfigQueryHandler configQueryHandler;
  private final ConfigWebConverter configWebConverter;
  private final ConfigSnapshotApplicationService configSnapshotApplicationService;

  private static final DateTimeFormatter EXPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  @Operation(summary = "创建配置")
  @PostMapping
  public ApiResponse<Long> create(@Valid @RequestBody CreateConfigReq req) {
    return ApiResponse.success(configCommandHandler.handle(configWebConverter.toCommand(req)));
  }

  @Operation(summary = "更新配置")
  @PutMapping
  public ApiResponse<Void> update(@Valid @RequestBody UpdateConfigReq req) {
    configCommandHandler.handle(configWebConverter.toCommand(req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除配置")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    configCommandHandler.delete(id);
    return ApiResponse.success();
  }

  @Operation(summary = "根据ID获取配置")
  @GetMapping("/{id}")
  public ApiResponse<ConfigResp> getById(@PathVariable Long id) {
    ConfigDTO dto = configQueryHandler.getById(id);
    return ApiResponse.success(dto != null ? configWebConverter.toResp(dto) : null);
  }

  @Operation(summary = "根据配置键获取配置")
  @GetMapping("/key/{key}")
  public ApiResponse<ConfigResp> getByKey(@PathVariable String key) {
    ConfigDTO dto = configQueryHandler.getByKey(key);
    return ApiResponse.success(dto != null ? configWebConverter.toResp(dto) : null);
  }

  @Operation(summary = "查询配置列表")
  @GetMapping
  public ApiResponse<PageResult<ConfigResp>> list(ConfigPageReq req) {
    PageResult<ConfigDTO> pageResult = configQueryHandler.page(configWebConverter.toQuery(req));
    return ApiResponse.success(pageResult.map(configWebConverter::toResp));
  }

  @Operation(summary = "分页查询配置列表")
  @GetMapping("/page")
  public ApiResponse<PageResult<ConfigResp>> page(ConfigPageReq req) {
    PageResult<ConfigDTO> pageResult = configQueryHandler.page(configWebConverter.toQuery(req));
    return ApiResponse.success(pageResult.map(configWebConverter::toResp));
  }

  @Operation(summary = "导出配置快照（JSON，加密项脱敏）")
  @PostMapping("/export")
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
  public ApiResponse<ConfigSnapshotImportResult> importConfig(
      @RequestParam("file") MultipartFile file) {
    try {
      String json = new String(file.getBytes(), StandardCharsets.UTF_8);
      return ApiResponse.success(configSnapshotApplicationService.importSnapshot(json));
    } catch (IOException e) {
      throw BizException.of("读取配置快照文件失败: " + e.getMessage());
    }
  }

  @Operation(summary = "配置变更历史")
  @GetMapping("/{id}/history")
  public ApiResponse<List<Map<String, Object>>> history(@PathVariable Long id) {
    // MVP-09 仅交付「配置（含功能开关）」的快照导出/导入与查询，未落地逐条变更历史表；
    // 返回空列表避免前端「配置历史」面板因 404 而报错。若需审计级历史，应建 SystemConfigHistory
    // 域事件订阅器（ADR-0030 C2：聚合内事件侧不可达，走投影），而非在此临时拼装。
    return ApiResponse.success(Collections.emptyList());
  }
}
