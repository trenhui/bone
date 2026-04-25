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
import com.bone.system.application.usecase.standard.CreateAlertRuleUseCase;
import com.bone.system.application.usecase.standard.UpdateAlertRuleUseCase;
import com.bone.system.application.usecase.standard.EnableAlertRuleUseCase;
import com.bone.system.application.usecase.standard.DisableAlertRuleUseCase;
import com.bone.system.application.usecase.standard.DeleteAlertRuleUseCase;
import com.bone.system.application.usecase.standard.CreateAlertEventUseCase;
import com.bone.system.application.usecase.standard.ResolveAlertUseCase;
import com.bone.system.application.usecase.standard.AlertRuleByIdQueryUseCase;
import com.bone.system.application.usecase.standard.AlertRulePageQueryUseCase;
import com.bone.system.application.usecase.standard.AlertEventByIdQueryUseCase;
import com.bone.system.application.usecase.standard.AlertEventPageQueryUseCase;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.common.result.ApiResponse;
import com.bone.system.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警管理控制器
 */
@Tag(name = "告警管理", description = "告警规则和告警事件管理接口")
@RestController
@RequestMapping("/api/system/alert")
@RequiredArgsConstructor
public class AlertController {
    private final CreateAlertRuleUseCase createAlertRuleUseCase;
    private final UpdateAlertRuleUseCase updateAlertRuleUseCase;
    private final EnableAlertRuleUseCase enableAlertRuleUseCase;
    private final DisableAlertRuleUseCase disableAlertRuleUseCase;
    private final DeleteAlertRuleUseCase deleteAlertRuleUseCase;
    private final CreateAlertEventUseCase createAlertEventUseCase;
    private final ResolveAlertUseCase resolveAlertUseCase;
    private final AlertRuleByIdQueryUseCase alertRuleByIdQueryUseCase;
    private final AlertRulePageQueryUseCase alertRulePageQueryUseCase;
    private final AlertEventByIdQueryUseCase alertEventByIdQueryUseCase;
    private final AlertEventPageQueryUseCase alertEventPageQueryUseCase;

    @Operation(summary = "创建告警规则")
    @PostMapping("/rules")
    public ApiResponse<Long> createRule(@Valid @RequestBody CreateAlertRuleReq req) {
        return ApiResponse.success(createAlertRuleUseCase.execute(AlertWebConverter.INSTANCE.toCmd(req)));
    }

    @Operation(summary = "更新告警规则")
    @PutMapping("/rules")
    public ApiResponse<Void> updateRule(@Valid @RequestBody UpdateAlertRuleReq req) {
        updateAlertRuleUseCase.execute(AlertWebConverter.INSTANCE.toCmd(req));
        return ApiResponse.success();
    }

    @Operation(summary = "启用告警规则")
    @PostMapping("/rules/{id}/enable")
    public ApiResponse<Void> enableRule(@PathVariable Long id) {
        EnableAlertRuleCmd cmd = new EnableAlertRuleCmd();
        cmd.setId(id);
        enableAlertRuleUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @Operation(summary = "禁用告警规则")
    @PostMapping("/rules/{id}/disable")
    public ApiResponse<Void> disableRule(@PathVariable Long id) {
        DisableAlertRuleCmd cmd = new DisableAlertRuleCmd();
        cmd.setId(id);
        disableAlertRuleUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @Operation(summary = "删除告警规则")
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        deleteAlertRuleUseCase.execute(id);
        return ApiResponse.success();
    }

    @Operation(summary = "根据ID获取告警规则")
    @GetMapping("/rules/{id}")
    public ApiResponse<AlertRuleResp> getRuleById(@PathVariable Long id) {
        AlertRuleDTO dto = alertRuleByIdQueryUseCase.execute(id);
        return ApiResponse.success(dto != null ? AlertWebConverter.INSTANCE.toResp(dto) : null);
    }

    @Operation(summary = "分页查询告警规则列表")
    @GetMapping("/rules/page")
    public ApiResponse<PageResult<AlertRuleResp>> pageRules(AlertRulePageReq req) {
        PageResult<AlertRuleDTO> pageResult = alertRulePageQueryUseCase.execute(AlertWebConverter.INSTANCE.toQry(req));
        return ApiResponse.success(pageResult.map(AlertWebConverter.INSTANCE::toResp));
    }

    @Operation(summary = "创建告警事件")
    @PostMapping("/events")
    public ApiResponse<Long> createEvent(@RequestParam Long ruleId, @RequestParam Double actualValue) {
        Map<String, Object> params = new HashMap<>();
        params.put("ruleId", ruleId);
        params.put("actualValue", actualValue);
        return ApiResponse.success(createAlertEventUseCase.execute(params));
    }

    @Operation(summary = "解决告警事件")
    @PostMapping("/events/{id}/resolve")
    public ApiResponse<Void> resolveEvent(@PathVariable Long id) {
        ResolveAlertCmd cmd = new ResolveAlertCmd();
        cmd.setId(id);
        resolveAlertUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @Operation(summary = "根据ID获取告警事件")
    @GetMapping("/events/{id}")
    public ApiResponse<AlertEventResp> getEventById(@PathVariable Long id) {
        AlertEventDTO dto = alertEventByIdQueryUseCase.execute(id);
        return ApiResponse.success(dto != null ? AlertWebConverter.INSTANCE.toResp(dto) : null);
    }

    @Operation(summary = "分页查询告警事件列表")
    @GetMapping("/events/page")
    public ApiResponse<PageResult<AlertEventResp>> pageEvents(AlertEventPageReq req) {
        int[] params = {req.getPageNum(), req.getPageSize()};
        PageResult<AlertEventDTO> pageResult = alertEventPageQueryUseCase.execute(params);
        return ApiResponse.success(pageResult.map(AlertWebConverter.INSTANCE::toResp));
    }
}
