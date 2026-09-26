package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.assembler.LogAssembler;
import com.bone.system.adapter.web.dto.request.CreateLogReq;
import com.bone.system.adapter.web.dto.request.LogExportReq;
import com.bone.system.adapter.web.dto.request.LogPageReq;
import com.bone.system.adapter.web.dto.response.LogResp;
import com.bone.system.application.LogExportApplicationService;
import com.bone.system.application.SystemLogApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 日志管理控制器。 */
@Tag(name = "日志管理", description = "系统日志管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/logs")
@RequiredArgsConstructor
public class LogController {

  private final SystemLogApplicationService systemLogApplicationService;
  private final LogExportApplicationService logExportApplicationService;
  private final LogAssembler logAssembler;

  private static final DateTimeFormatter EXPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  @Operation(summary = "创建日志")
  @PostMapping
  @PreAuthorize("hasAuthority('sys:log:write')")
  public ApiResponse<Long> create(@Valid @RequestBody CreateLogReq req) {
    return ApiResponse.success(systemLogApplicationService.create(logAssembler.toCommand(req)));
  }

  @Operation(summary = "根据ID获取日志")
  @GetMapping("/{id}")
  public ApiResponse<LogResp> getById(@PathVariable Long id) {
    return ApiResponse.success(
        systemLogApplicationService.getById(id).map(logAssembler::toResp).orElse(null));
  }

  @Operation(summary = "查询日志列表")
  @GetMapping
  public ApiResponse<PageResult<LogResp>> list(LogPageReq req) {
    return page(req);
  }

  @Operation(summary = "分页查询日志列表")
  @GetMapping("/page")
  public ApiResponse<PageResult<LogResp>> page(LogPageReq req) {
    return ApiResponse.success(
        systemLogApplicationService.page(logAssembler.toQuery(req)).map(logAssembler::toResp));
  }

  @Operation(summary = "导出日志（CSV）", description = "导出全量日志数据，需 sys:log:write 权限（平台域）")
  @PostMapping("/export")
  @PreAuthorize("hasAuthority('sys:log:write')")
  public ResponseEntity<byte[]> export(@RequestBody(required = false) LogExportReq req) {
    byte[] bytes = logExportApplicationService.exportCsv(logAssembler.toExportQuery(req));
    String ts = LocalDateTime.now().format(EXPORT_TS);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"logs-" + ts + ".csv\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(bytes);
  }

  @Operation(summary = "日志分析概览（总量 + 各级别分布）")
  @GetMapping("/analyze")
  public ApiResponse<Map<String, Object>> analyze() {
    return ApiResponse.success(systemLogApplicationService.analyze());
  }
}
