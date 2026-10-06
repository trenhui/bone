package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.application.ChannelApplicationService;
import com.bone.blueprint.application.query.dto.ChannelDto;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道 Web 接口（多渠道交易域）。
 *
 * <p><b>授权</b>：读 {@code commerce:channel:read}、写 {@code commerce:channel:write}。
 * 渠道启停会影响真实交易链路（停掉淘宝即停止拉单）， 属写操作必须收权限，不能只依赖「已登录」。
 */
@Tag(name = "销售渠道管理", description = "多渠道（淘宝/京东/抖音/拼多多）渠道的注册、启停与查询")
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

  private final ChannelApplicationService channelApplicationService;

  @Operation(summary = "渠道分页列表")
  @PreAuthorize("hasAuthority('commerce:channel:read')")
  @GetMapping
  public ApiResponse<PageResult<ChannelDto>> page(
      @RequestParam(required = false) String channelCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(channelApplicationService.page(channelCode, page, size));
  }

  @Operation(summary = "渠道详情")
  @PreAuthorize("hasAuthority('commerce:channel:read')")
  @GetMapping("/{channelCode}")
  public ApiResponse<ChannelDto> detail(@PathVariable String channelCode) {
    return ApiResponse.success(channelApplicationService.detail(channelCode));
  }

  @Operation(summary = "注册渠道（幂等：已存在直接返回）")
  @PreAuthorize("hasAuthority('commerce:channel:write')")
  @PostMapping
  public ApiResponse<ChannelDto> register(
      @RequestParam String channelCode,
      @RequestParam(required = false) String apiEndpoint,
      @RequestParam(required = false) String appKey) {
    return ApiResponse.success(
        channelApplicationService.register(channelCode, apiEndpoint, appKey));
  }

  @Operation(summary = "启用渠道")
  @PreAuthorize("hasAuthority('commerce:channel:write')")
  @PostMapping("/{channelCode}/enable")
  public ApiResponse<ChannelDto> enable(@PathVariable String channelCode) {
    return ApiResponse.success(channelApplicationService.enable(channelCode));
  }

  @Operation(summary = "停用渠道（同时关闭订单自动同步）")
  @PreAuthorize("hasAuthority('commerce:channel:write')")
  @PostMapping("/{channelCode}/disable")
  public ApiResponse<ChannelDto> disable(@PathVariable String channelCode) {
    return ApiResponse.success(channelApplicationService.disable(channelCode));
  }

  @Operation(summary = "设置渠道订单自动同步开关")
  @PreAuthorize("hasAuthority('commerce:channel:write')")
  @PostMapping("/{channelCode}/order-sync")
  public ApiResponse<ChannelDto> setOrderSync(
      @PathVariable String channelCode, @RequestParam boolean enabled) {
    return ApiResponse.success(channelApplicationService.setOrderSync(channelCode, enabled));
  }
}
