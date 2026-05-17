package com.bone.studio.generator.adapter.web.controller;

import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.common.result.ApiResponse;
import com.bone.studio.generator.infrastructure.handler.HandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(GeneratorApiPaths.CAPABILITIES)
@RequiredArgsConstructor
public class CapabilityController {

    private final HandlerRegistry handlerRegistry;

    @GetMapping
    public ApiResponse<?> getAllCapabilities() {
        return ApiResponse.success(handlerRegistry.getAllCapabilities());
    }

    @GetMapping("/{name}")
    public ApiResponse<?> getCapability(@PathVariable String name) {
        return ApiResponse.success(handlerRegistry.getCapability(name));
    }
}
