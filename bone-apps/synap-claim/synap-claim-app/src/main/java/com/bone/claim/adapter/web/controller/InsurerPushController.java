package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.InsurerPushWriter;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保司推送查询接口（结案报送保司的可观测出口）。
 *
 * <p>推送事件由 {@link InsurerPushWriter} 在支付结案同事务写入 Outbox，经既有中继投递；本接口只读该事件状态，
 * 供核赔工作台/联调核对「案件结案后是否已完成保司报送」。路径挂在 {@code /api/v1/claims/**} 下，网关已登记该前缀。
 */
@Tag(name = "智能理赔-保司推送", description = "结案保司报送状态查询")
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class InsurerPushController {

  private final InsurerPushWriter insurerPushWriter;

  @Operation(
      summary = "查询赔案的保司推送状态",
      description = "返回 Outbox 中 INSURER_PUSH 事件的 status/payload/retryCount；未结案或未登记返回 data=null")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/{claimId}/insurer-push")
  public ApiResponse<InsurerPushWriter.PushStatus> status(@PathVariable long claimId) {
    return ApiResponse.success(HttpStatus.OK.value(), insurerPushWriter.findByClaim(claimId));
  }
}
