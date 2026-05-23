package com.bone.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.capability.HandlerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "能力管理", description = "AI/Flow 能力发现接口")
@RestController
@RequestMapping("/api/capabilities")
@RequiredArgsConstructor
public class CapabilityController {
    
    private final HandlerRegistry handlerRegistry;
    
    @Operation(summary = "获取所有能力", description = "返回所有可被 AI/Flow 调用的能力清单")
    @GetMapping
    public ApiResponse<List<HandlerRegistry.CapabilityRegistration>> getAllCapabilities() {
        return ApiResponse.success(handlerRegistry.getAllCapabilities());
    }
    
    @Operation(summary = "获取指定能力", description = "根据能力名称获取详细元数据")
    @GetMapping("/{name}")
    public ApiResponse<HandlerRegistry.CapabilityRegistration> getCapability(@PathVariable String name) {
        return ApiResponse.success(handlerRegistry.getCapability(name));
    }
}
