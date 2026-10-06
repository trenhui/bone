package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.application.ChannelBroadcastApplicationService;
import com.bone.blueprint.application.port.out.ChannelBroadcastQueryPort;
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
 * 渠道库存广播任务 Web 接口（Outbox 运维面）。
 *
 * <p><b>为什么需要这个页面</b>：库存广播是「渠道库存必须跟上实物库存」的唯一防线， 一旦广播失败而无人知道， 渠道会继续按旧库存售卖直到超卖。
 * 中继已提供自动退避重试与死信，但渠道侧持续失败（凭证过期、资质失效、类目下线）重试再多次也不会成功， 必须有人介入—— 这就是「失败清单 + 一键重试」存在的理由。
 *
 * <p><b>授权</b>：读 {@code commerce:broadcast:read}、写 {@code commerce:broadcast:write}。
 * 重试会真实调用渠道接口并消耗配额，不是只读操作。
 */
@Tag(name = "渠道库存广播", description = "库存广播任务（Outbox 异步投递）的失败清单、人工重试与手动推送")
@RestController
@RequestMapping("/api/v1/channel-broadcasts")
@RequiredArgsConstructor
public class ChannelBroadcastController {

  private final ChannelBroadcastApplicationService channelBroadcastApplicationService;

  @Operation(summary = "广播任务分页（status 可选：PENDING/PROCESSING/SENT/FAILED）")
  @PreAuthorize("hasAuthority('commerce:broadcast:read')")
  @GetMapping
  public ApiResponse<PageResult<ChannelBroadcastQueryPort.TaskView>> page(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(channelBroadcastApplicationService.page(status, page, size));
  }

  @Operation(summary = "人工重试一条广播任务（回队列并立即推进一轮中继）")
  @PreAuthorize("hasAuthority('commerce:broadcast:write')")
  @PostMapping("/{taskId}/retry")
  public ApiResponse<ChannelBroadcastQueryPort.TaskView> retry(
      @org.springframework.web.bind.annotation.PathVariable String taskId) {
    return ApiResponse.success(channelBroadcastApplicationService.retry(taskId));
  }

  @Operation(summary = "立即推进一轮广播中继（不等下一个调度周期）")
  @PreAuthorize("hasAuthority('commerce:broadcast:write')")
  @PostMapping("/relay-now")
  public ApiResponse<List<Integer>> relayNow() {
    int sent = channelBroadcastApplicationService.relayNow();
    return ApiResponse.success(List.of(sent));
  }
}
