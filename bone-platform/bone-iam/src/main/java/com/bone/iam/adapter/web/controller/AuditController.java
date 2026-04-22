package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.AuditLogDTO;
import com.bone.iam.application.query.handler.AuditLogListQueryHandler;
import com.bone.iam.application.query.qry.AuditLogListQry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditLogListQueryHandler auditLogListQueryHandler;

    @GetMapping("/logs")
    public ApiResponse<PageResult<AuditLogDTO>> logs(AuditLogListQry qry) {
        PageResult<AuditLogDTO> result = auditLogListQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/logs/export")
    public ApiResponse<?> export() {
        // 实现导出审计日志逻辑
        return ApiResponse.success();
    }

    @GetMapping("/settings")
    public ApiResponse<?> settings() {
        // 实现获取审计设置逻辑
        return ApiResponse.success();
    }

    @PutMapping("/settings")
    public ApiResponse<?> updateSettings() {
        // 实现更新审计设置逻辑
        return ApiResponse.success();
    }
}