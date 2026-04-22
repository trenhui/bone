package com.bone.system.adapter.web.controller;

import com.bone.system.adapter.web.converter.AlertWebConverter;
import com.bone.system.adapter.web.dto.req.AlertEventPageReq;
import com.bone.system.adapter.web.dto.req.AlertRulePageReq;
import com.bone.system.adapter.web.dto.req.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.req.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.resp.AlertEventResp;
import com.bone.system.adapter.web.dto.resp.AlertRuleResp;
import com.bone.system.application.command.cmd.DisableAlertRuleCmd;
import com.bone.system.application.command.cmd.EnableAlertRuleCmd;
import com.bone.system.application.command.cmd.ResolveAlertCmd;
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
@RequestMapping("/api/system/alert")
@RequiredArgsConstructor
public class AlertController {
    private final AlertCommandHandler alertCommandHandler;
    private final AlertQueryHandler alertQueryHandler;

    @Operation(summary = "创建告警规则")
    @PostMapping("/rules")
    public ApiResponse<Long> createRule(@Valid @RequestBody CreateAlertRuleReq req) {
        return ApiResponse.success(alertCommandHandler.handle(AlertWebConverter.INSTANCE.toCmd(req)));
    }

    @Operation(summary = "更新告警规则")
    @PutMapping("/rules")
    public ApiResponse<Void> updateRule(@Valid @RequestBody UpdateAlertRuleReq req) {
        alertCommandHandler.handle(AlertWebConverter.INSTANCE.toCmd(req));
        return ApiResponse.success();
    }

    @Operation(summary = "启用告警规则")
    @PostMapping("/rules/{id}/enable")
    public ApiResponse<Void> enableRule(@PathVariable Long id) {
        EnableAlertRuleCmd cmd = new EnableAlertRuleCmd();
        cmd.setId(id);
        alertCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @Operation(summary = "禁用告警规则")
    @PostMapping("/rules/{id}/disable")
    public ApiResponse<Void> disableRule(@PathVariable Long id) {
        DisableAlertRuleCmd cmd = new DisableAlertRuleCmd();
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
        return ApiResponse.success(dto != null ? AlertWebConverter.INSTANCE.toResp(dto) : null);
    }

    @Operation(summary = "分页查询告警规则列表")
    @GetMapping("/rules/page")
    public ApiResponse<PageResult<AlertRuleResp>> pageRules(AlertRulePageReq req) {
        PageResult<AlertRuleDTO> pageResult = alertQueryHandler.pageRules(AlertWebConverter.INSTANCE.toQry(req));
        return ApiResponse.success(pageResult.map(AlertWebConverter.INSTANCE::toResp));
    }

    @Operation(summary = "创建告警事件")
    @PostMapping("/events")
    public ApiResponse<Long> createEvent(@RequestParam Long ruleId, @RequestParam Double actualValue) {
        return ApiResponse.success(alertCommandHandler.createAlertEvent(ruleId, actualValue));
    }

    @Operation(summary = "解决告警事件")
    @PostMapping("/events/{id}/resolve")
    public ApiResponse<Void> resolveEvent(@PathVariable Long id) {
        ResolveAlertCmd cmd = new ResolveAlertCmd();
        cmd.setId(id);
        alertCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @Operation(summary = "根据ID获取告警事件")
    @GetMapping("/events/{id}")
    public ApiResponse<AlertEventResp> getEventById(@PathVariable Long id) {
        AlertEventDTO dto = alertQueryHandler.getEventById(id);
        return ApiResponse.success(dto != null ? AlertWebConverter.INSTANCE.toResp(dto) : null);
    }

    @Operation(summary = "分页查询告警事件列表")
    @GetMapping("/events/page")
    public ApiResponse<PageResult<AlertEventResp>> pageEvents(AlertEventPageReq req) {
        PageResult<AlertEventDTO> pageResult = alertQueryHandler.pageEvents(req.getPageNum(), req.getPageSize());
        return ApiResponse.success(pageResult.map(AlertWebConverter.INSTANCE::toResp));
    }
}
