package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.converter.ConfigWebConverter;
import com.bone.system.adapter.web.dto.req.ConfigPageReq;
import com.bone.system.adapter.web.dto.req.CreateConfigReq;
import com.bone.system.adapter.web.dto.req.UpdateConfigReq;
import com.bone.system.adapter.web.dto.resp.ConfigResp;
import com.bone.system.application.command.handler.ConfigCommandHandler;
import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.application.query.handler.ConfigQueryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 系统配置控制器 */
@Tag(name = "系统配置", description = "系统配置管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/config")
@RequiredArgsConstructor
public class ConfigController {

  private final ConfigCommandHandler configCommandHandler;
  private final ConfigQueryHandler configQueryHandler;
  private final ConfigWebConverter configWebConverter;

  @Operation(summary = "创建配置")
  @PostMapping
  public ApiResponse<Long> create(@Valid @RequestBody CreateConfigReq req) {
    return ApiResponse.success(configCommandHandler.handle(configWebConverter.toCommand(req)));
  }

  @Operation(summary = "更新配置")
  @PutMapping
  public ApiResponse<Void> update(@Valid @RequestBody UpdateConfigReq req) {
    configCommandHandler.handle(configWebConverter.toCommand(req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除配置")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    configCommandHandler.delete(id);
    return ApiResponse.success();
  }

  @Operation(summary = "根据ID获取配置")
  @GetMapping("/{id}")
  public ApiResponse<ConfigResp> getById(@PathVariable Long id) {
    ConfigDTO dto = configQueryHandler.getById(id);
    return ApiResponse.success(dto != null ? configWebConverter.toResp(dto) : null);
  }

  @Operation(summary = "根据配置键获取配置")
  @GetMapping("/key/{key}")
  public ApiResponse<ConfigResp> getByKey(@PathVariable String key) {
    ConfigDTO dto = configQueryHandler.getByKey(key);
    return ApiResponse.success(dto != null ? configWebConverter.toResp(dto) : null);
  }

  @Operation(summary = "查询配置列表")
  @GetMapping
  public ApiResponse<PageResult<ConfigResp>> list(ConfigPageReq req) {
    PageResult<ConfigDTO> pageResult = configQueryHandler.page(configWebConverter.toQuery(req));
    return ApiResponse.success(pageResult.map(configWebConverter::toResp));
  }

  @Operation(summary = "分页查询配置列表")
  @GetMapping("/page")
  public ApiResponse<PageResult<ConfigResp>> page(ConfigPageReq req) {
    PageResult<ConfigDTO> pageResult = configQueryHandler.page(configWebConverter.toQuery(req));
    return ApiResponse.success(pageResult.map(configWebConverter::toResp));
  }
}
