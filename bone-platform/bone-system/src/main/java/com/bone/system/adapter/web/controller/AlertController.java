package com.bone.system.adapter.web.controller;

import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.converter.AlertWebConverter;
import com.bone.system.adapter.web.dto.req.AlertEventPageReq;
import com.bone.system.adapter.web.dto.req.AlertRulePageReq;
import com.bone.system.adapter.web.dto.req.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.req.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.resp.AlertEventResp;
import com.bone.system.adapter.web.dto.resp.AlertRuleResp;
import com.bone.system.application.command.cmd.DisableAlertRuleCommand;
import com.bone.system.application.command.cmd.EnableAlertRuleCommand;
import com.bone.system.application.command.cmd.ResolveAlertCommand;
import com.bone.system.application.command.handler.AlertCommandHandler;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.handler.AlertQueryHandler;
import com.bone.system.common.result.ApiResponse;
import com.bone.system.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 告警管理控制器
 */
@Tag(name = "告警管理", description = "告警规则和告警事件管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertCommandHandler alertCommandHandler;
    private final AlertQueryHandler alertQueryHandler;
    private final AlertWebConverter alertWebConverter;

    @Operation(summary = "创建告警规则")
    @PostMapping("/rules")
    public ApiResponse<Long> createRule(@Valid @RequestBody CreateAlertRuleReq req) {
        return ApiResponse.success(alertCommandHandler.handle(alertWebConverter.toCommand(req)));
    }

    @Operation(summary = "更新告警规则")
    @PutMapping("/rules")
    public ApiResponse<Void> updateRule(@Valid @RequestBody UpdateAlertRuleReq req) {
        alertCommandHandler.handle(alertWebConverter.toCommand(req));
        return ApiResponse.success();
    }

    @Operation(summary = "启用告警规则")
    @PostMapping("/rules/{id}/enable")
    public ApiResponse<Void> enableRule(@PathVariable Long id) {
        EnableAlertRuleCommand cmd = new EnableAlertRuleCommand();
        cmd.setId(id);
        alertCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @Operation(summary = "禁用告警规则")
    @PostMapping("/rules/{id}/disable")
    public ApiResponse<Void> disableRule(@PathVariable Long id) {
        DisableAlertRuleCommand cmd = new DisableAlertRuleCommand();
        cmd.setId(id);
        alertCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @Operation(summary = "删除告警规则")
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        alertCommandHandler.delete(id);
        return ApiResponse.success();
    }

    @Operation(summary = "根据ID获取告警规则")
    @GetMapping("/rules/{id}")
    public ApiResponse<AlertRuleResp> getRuleById(@PathVariable Long id) {
        AlertRuleDTO dto = alertQueryHandler.getRuleById(id);
        return ApiResponse.success(dto != null ? alertWebConverter.toResp(dto) : null);
    }

    @Operation(summary = "分页查询告警规则列表")
    @GetMapping("/rules/page")
    public ApiResponse<PageResult<AlertRuleResp>> pageRules(AlertRulePageReq req) {
        PageResult<AlertRuleDTO> pageResult = alertQueryHandler.pageRules(alertWebConverter.toQuery(req));
        return ApiResponse.success(pageResult.map(alertWebConverter::toResp));
    }

    @Operation(summary = "创建告警事件")
    @PostMapping("/events")
    public ApiResponse<Long> createEvent(@RequestParam Long ruleId, @RequestParam Double actualValue) {
        return ApiResponse.success(alertCommandHandler.createAlertEvent(ruleId, actualValue));
    }

    @Operation(summary = "解决告警事件")
    @PostMapping("/events/{id}/resolve")
    public ApiResponse<Void> resolveEvent(@PathVariable Long id) {
        ResolveAlertCommand cmd = new ResolveAlertCommand();
        cmd.setId(id);
        alertCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @Operation(summary = "根据ID获取告警事件")
    @GetMapping("/events/{id}")
    public ApiResponse<AlertEventResp> getEventById(@PathVariable Long id) {
        AlertEventDTO dto = alertQueryHandler.getEventById(id);
        return ApiResponse.success(dto != null ? alertWebConverter.toResp(dto) : null);
    }

    @Operation(summary = "分页查询告警事件列表")
    @GetMapping("/events/page")
    public ApiResponse<PageResult<AlertEventResp>> pageEvents(AlertEventPageReq req) {
        PageResult<AlertEventDTO> pageResult =
                alertQueryHandler.pageEvents(req.getPageNum(), req.getPageSize());
        return ApiResponse.success(pageResult.map(alertWebConverter::toResp));
    }
}
