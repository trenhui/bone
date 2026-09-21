package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.assembler.AlertAssembler;
import com.bone.system.adapter.web.dto.request.AlertRecordPageReq;
import com.bone.system.adapter.web.dto.request.AlertRulePageReq;
import com.bone.system.adapter.web.dto.request.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.request.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.response.AlertRecordResp;
import com.bone.system.adapter.web.dto.response.AlertRuleResp;
import com.bone.system.application.AlertApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 告警管理控制器。 */
@Tag(name = "告警管理", description = "告警规则和告警事件管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/alert")
@RequiredArgsConstructor
public class AlertController {

  private final AlertApplicationService alertApplicationService;
  private final AlertAssembler alertAssembler;

  @Operation(summary = "创建告警规则")
  @PostMapping("/rules")
  public ApiResponse<Long> createRule(@Valid @RequestBody CreateAlertRuleReq req) {
    return ApiResponse.success(alertApplicationService.createRule(alertAssembler.toCommand(req)));
  }

  @Operation(summary = "更新告警规则")
  @PutMapping("/rules")
  public ApiResponse<Void> updateRule(@Valid @RequestBody UpdateAlertRuleReq req) {
    alertApplicationService.updateRule(alertAssembler.toCommand(req));
    return ApiResponse.success();
  }

  @Operation(summary = "启用告警规则")
  @PostMapping("/rules/{id}/enable")
  public ApiResponse<Void> enableRule(@PathVariable Long id) {
    alertApplicationService.enableRule(id);
    return ApiResponse.success();
  }

  @Operation(summary = "禁用告警规则")
  @PostMapping("/rules/{id}/disable")
  public ApiResponse<Void> disableRule(@PathVariable Long id) {
    alertApplicationService.disableRule(id);
    return ApiResponse.success();
  }

  @Operation(summary = "删除告警规则")
  @DeleteMapping("/rules/{id}")
  public ApiResponse<Void> deleteRule(@PathVariable Long id) {
    alertApplicationService.deleteRule(id);
    return ApiResponse.success();
  }

  @Operation(summary = "根据ID获取告警规则")
  @GetMapping("/rules/{id}")
  public ApiResponse<AlertRuleResp> getRuleById(@PathVariable Long id) {
    return ApiResponse.success(
        alertApplicationService.getRuleById(id).map(alertAssembler::toResp).orElse(null));
  }

  @Operation(summary = "查询告警规则列表")
  @GetMapping("/rules")
  public ApiResponse<PageResult<AlertRuleResp>> listRules(AlertRulePageReq req) {
    return pageRules(req);
  }

  @Operation(summary = "分页查询告警规则列表")
  @GetMapping("/rules/page")
  public ApiResponse<PageResult<AlertRuleResp>> pageRules(AlertRulePageReq req) {
    return ApiResponse.success(
        alertApplicationService.pageRules(alertAssembler.toQuery(req)).map(alertAssembler::toResp));
  }

  /**
   * 上报实测值并按需生成告警记录。
   *
   * <p>未达阈值时返回 {@code null} data（不是错误）——「上报了但没告警」是正常结果。
   */
  @Operation(summary = "上报指标触发告警（达阈值才生成记录）")
  @PostMapping("/events")
  public ApiResponse<Long> recordIfTriggered(
      @RequestParam Long ruleId, @RequestParam Double actualValue) {
    return ApiResponse.success(
        alertApplicationService.recordIfTriggered(ruleId, actualValue).orElse(null));
  }

  @Operation(summary = "解决告警事件")
  @PostMapping("/events/{id}/resolve")
  public ApiResponse<Void> resolveEvent(@PathVariable Long id) {
    alertApplicationService.resolveRecord(id);
    return ApiResponse.success();
  }

  @Operation(summary = "根据ID获取告警事件")
  @GetMapping("/events/{id}")
  public ApiResponse<AlertRecordResp> getEventById(@PathVariable Long id) {
    return ApiResponse.success(
        alertApplicationService.getRecordById(id).map(alertAssembler::toResp).orElse(null));
  }

  @Operation(summary = "查询告警事件列表")
  @GetMapping("/events")
  public ApiResponse<PageResult<AlertRecordResp>> listEvents(AlertRecordPageReq req) {
    return pageEvents(req);
  }

  @Operation(summary = "分页查询告警事件列表")
  @GetMapping("/events/page")
  public ApiResponse<PageResult<AlertRecordResp>> pageEvents(AlertRecordPageReq req) {
    return ApiResponse.success(
        alertApplicationService
            .pageRecords(alertAssembler.toRecordQuery(req))
            .map(alertAssembler::toResp));
  }
}
