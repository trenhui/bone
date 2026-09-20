package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.AuditWebConverter;
import com.bone.iam.adapter.web.dto.response.AuditSettingsResp;
import com.bone.iam.application.AuditApplicationService;
import com.bone.iam.application.command.cmd.UpdateAuditSettingsCommand;
import com.bone.iam.application.query.dto.AuditLogDTO;
import com.bone.iam.application.query.qry.AuditLogListQuery;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 审计日志控制器 提供审计日志查询、导出功能，以及审计设置管理 */
@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/audit")
@RequiredArgsConstructor
public class AuditController {

  private final AuditApplicationService auditApplicationService;
  private final AuditWebConverter auditWebConverter;

  /** 分页查询审计日志 支持按用户、操作类型、时间范围等条件过滤 */
  @GetMapping("/logs")
  @PreAuthorize("hasAuthority('iam:audit:read')")
  public ApiResponse<PageResult<AuditLogDTO>> logs(AuditLogListQuery qry) {
    PageResult<AuditLogDTO> result = auditApplicationService.listLogs(qry);
    return ApiResponse.success(result);
  }

  /** 单次 CSV 导出上限（详设 §7.1，超出请按时间窗口分批）。 */
  private static final int EXPORT_MAX_SIZE = 10000;

  private static final String CSV_HEADER =
      "id,tenant_id,user_id,operation,resource_type,resource_id,ip,user_agent,result,duration_ms,created_at";

  private static final DateTimeFormatter CSV_TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  /**
   * 导出审计日志为 CSV（UTF-8 + BOM）。
   *
   * <p>响应头：{@code Content-Type=text/csv;charset=utf-8} + {@code Content-Disposition=attachment;
   * filename=iam-audit-logs.csv}； 超过 {@value #EXPORT_MAX_SIZE} 条会截断（日志含警告），与账号导出一致。
   */
  @GetMapping("/logs/export")
  @PreAuthorize("hasAuthority('iam:audit:read')")
  public ResponseEntity<byte[]> export(AuditLogListQuery qry) {
    qry.setSize(EXPORT_MAX_SIZE);
    PageResult<AuditLogDTO> result = auditApplicationService.listLogs(qry);

    StringBuilder sb = new StringBuilder();
    sb.append('\uFEFF'); // BOM，让 Excel 直接按 UTF-8 解析
    sb.append(CSV_HEADER).append('\n');
    for (AuditLogDTO log : result.getRecords()) {
      sb.append(safe(log.getId()))
          .append(',')
          .append(safe(log.getTenantId()))
          .append(',')
          .append(safe(log.getUserId()))
          .append(',')
          .append(escape(log.getOperation() == null ? "" : log.getOperation().name()))
          .append(',')
          .append(escape(log.getResourceType()))
          .append(',')
          .append(escape(log.getResourceId()))
          .append(',')
          .append(escape(log.getIp()))
          .append(',')
          .append(escape(log.getUserAgent()))
          .append(',')
          .append(escape(log.getResult()))
          .append(',')
          .append(safe(log.getDuration()))
          .append(',')
          .append(escape(log.getCreatedAt() == null ? "" : log.getCreatedAt().format(CSV_TS)))
          .append('\n');
    }
    byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv;charset=utf-8"));
    headers.setContentDisposition(
        org.springframework.http.ContentDisposition.attachment()
            .filename("iam-audit-logs.csv")
            .build());
    return ResponseEntity.ok().headers(headers).body(body);
  }

  private static String safe(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static String escape(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    boolean needQuote =
        value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
    String escaped = value.replace("\"", "\"\"");
    return needQuote ? "\"" + escaped + "\"" : escaped;
  }

  /** 获取审计设置 包括保留周期、日志存储位置、自动归档设置等 */
  @GetMapping("/settings")
  @PreAuthorize("hasAuthority('iam:audit:read')")
  public ApiResponse<AuditSettingsResp> settings() {
    return ApiResponse.success(auditWebConverter.toResp(auditApplicationService.getSettings()));
  }

  /** 更新审计设置 */
  @PutMapping("/settings")
  @PreAuthorize("hasAuthority('iam:audit:write')")
  public ApiResponse<Void> updateSettings(@RequestBody Map<String, Object> settings) {
    UpdateAuditSettingsCommand cmd = new UpdateAuditSettingsCommand();
    cmd.setSettings(settings);
    auditApplicationService.updateSettings(cmd);
    return ApiResponse.success();
  }
}
