package com.bone.system.adapter.web.controller;

import com.bone.system.adapter.web.converter.LogWebConverter;
import com.bone.system.adapter.web.dto.req.CreateLogReq;
import com.bone.system.adapter.web.dto.req.LogPageReq;
import com.bone.system.adapter.web.dto.resp.LogResp;
import com.bone.system.application.command.handler.LogCommandHandler;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.handler.LogQueryHandler;
import com.bone.system.common.result.ApiResponse;
import com.bone.system.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 日志管理控制器
 */
@Tag(name = "日志管理", description = "系统日志管理接口")
@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class LogController {
    private final LogCommandHandler logCommandHandler;
    private final LogQueryHandler logQueryHandler;

    @Operation(summary = "创建日志")
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CreateLogReq req) {
        return ApiResponse.success(logCommandHandler.handle(LogWebConverter.INSTANCE.toCmd(req)));
    }

    @Operation(summary = "根据ID获取日志")
    @GetMapping("/{id}")
    public ApiResponse<LogResp> getById(@PathVariable Long id) {
        LogDTO dto = logQueryHandler.getById(id);
        return ApiResponse.success(dto != null ? LogWebConverter.INSTANCE.toResp(dto) : null);
    }

    @Operation(summary = "分页查询日志列表")
    @GetMapping("/page")
    public ApiResponse<PageResult<LogResp>> page(LogPageReq req) {
        PageResult<LogDTO> pageResult = logQueryHandler.page(LogWebConverter.INSTANCE.toQry(req));
        return ApiResponse.success(pageResult.map(LogWebConverter.INSTANCE::toResp));
    }
}
