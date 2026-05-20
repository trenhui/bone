package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.AuditLogDTO;
import com.bone.iam.application.query.qry.AuditLogListQry;
import com.bone.iam.application.usecase.standard.AuditLogQueryUseCase;
import com.bone.iam.adapter.web.dto.resp.AuditSettingsResp;
import com.bone.iam.infrastructure.persistence.AuditSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审计日志控制器
 * 提供审计日志查询、导出功能，以及审计设置管理
 */
@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogQueryUseCase auditLogQueryUseCase;
    private final AuditSettingsRepository auditSettingsRepository;

    /**
     * 分页查询审计日志
     * 支持按用户、操作类型、时间范围等条件过滤
     */
    @GetMapping("/logs")
    public ApiResponse<PageResult<AuditLogDTO>> logs(AuditLogListQry qry) {
        PageResult<AuditLogDTO> result = auditLogQueryUseCase.execute(qry);
        return ApiResponse.success(result);
    }

    /**
     * 导出审计日志
     * 支持导出为Excel或CSV格式
     */
    @GetMapping("/logs/export")
    public ApiResponse<List<AuditLogDTO>> export(AuditLogListQry qry) {
        qry.setSize(10000); // 导出全部
        PageResult<AuditLogDTO> result = auditLogQueryUseCase.execute(qry);
        return ApiResponse.success(result.getRecords());
    }

    /**
     * 获取审计设置
     * 包括保留周期、日志存储位置、自动归档设置等
     */
    @GetMapping("/settings")
    public ApiResponse<AuditSettingsResp> settings() {
        return ApiResponse.success(auditSettingsRepository.findByTenant(auditSettingsRepository.resolveTenantId()));
    }

    /**
     * 更新审计设置
     */
    @PutMapping("/settings")
    public ApiResponse<Void> updateSettings(@RequestBody Map<String, Object> settings) {
        auditSettingsRepository.upsert(auditSettingsRepository.resolveTenantId(), settings);
        return ApiResponse.success();
    }
}
