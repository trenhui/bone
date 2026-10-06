package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.application.ChannelBuyerApplicationService;
import com.bone.blueprint.application.query.dto.ChannelBuyerDto;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道买家映射 Web 接口（多渠道交易域）。
 *
 * <p><b>授权</b>：读 {@code commerce:channel-buyer:read}、写 {@code commerce:channel-buyer:write}。
 * 绑定/改绑会改变历史订单的客户归属， 属写操作且影响对账，绝不能只靠「已登录」放行。
 *
 * <p><b>改绑与绑定为何分成两个端点</b>：改绑要填原因（留痕），日常绑定不需要。 合成一个带 {@code force} 参数的端点会让「顺手改绑」变得容易发生，
 * 而这类错误要等对账差异出来才被发现。
 */
@Tag(name = "渠道买家映射", description = "渠道买家账号与内部客户的映射：查询、绑定、改绑、解绑")
@RestController
@RequestMapping("/api/v1/channel-buyers")
@RequiredArgsConstructor
public class ChannelBuyerController {

  private final ChannelBuyerApplicationService channelBuyerApplicationService;

  @Operation(summary = "渠道买家映射分页列表")
  @PreAuthorize("hasAuthority('commerce:channel-buyer:read')")
  @GetMapping
  public ApiResponse<PageResult<ChannelBuyerDto>> page(
      @RequestParam(required = false) String channelCode,
      @RequestParam(required = false) Boolean bound,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(channelBuyerApplicationService.page(channelCode, bound, page, size));
  }

  @Operation(summary = "待绑定影子清单（按最近拉单时间倒序，运营优先处理高频买家）")
  @PreAuthorize("hasAuthority('commerce:channel-buyer:read')")
  @GetMapping("/shadow-candidates")
  public ApiResponse<List<ChannelBuyerDto>> shadowCandidates(
      @RequestParam(required = false) String channelCode,
      @RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.success(channelBuyerApplicationService.shadowCandidates(channelCode, limit));
  }

  @Operation(summary = "绑定到内部客户（仅未绑定时可绑）")
  @PreAuthorize("hasAuthority('commerce:channel-buyer:write')")
  @PostMapping("/bind")
  public ApiResponse<ChannelBuyerDto> bind(
      @RequestParam String channelCode,
      @RequestParam String channelBuyerId,
      @RequestParam Long customerId,
      @RequestParam(required = false) String customerName) {
    return ApiResponse.success(
        channelBuyerApplicationService.bind(channelCode, channelBuyerId, customerId, customerName));
  }

  @Operation(summary = "改绑到另一内部客户（必须填原因，留痕）")
  @PreAuthorize("hasAuthority('commerce:channel-buyer:write')")
  @PostMapping("/rebind")
  public ApiResponse<ChannelBuyerDto> rebind(
      @RequestParam String channelCode,
      @RequestParam String channelBuyerId,
      @RequestParam Long customerId,
      @RequestParam(required = false) String customerName,
      @RequestParam String reason) {
    return ApiResponse.success(
        channelBuyerApplicationService.rebind(
            channelCode, channelBuyerId, customerId, customerName, reason));
  }

  @Operation(summary = "解绑（退回影子状态，后续订单落「未知客户」）")
  @PreAuthorize("hasAuthority('commerce:channel-buyer:write')")
  @PostMapping("/unbind")
  public ApiResponse<ChannelBuyerDto> unbind(
      @RequestParam String channelCode, @RequestParam String channelBuyerId) {
    return ApiResponse.success(channelBuyerApplicationService.unbind(channelCode, channelBuyerId));
  }
}
