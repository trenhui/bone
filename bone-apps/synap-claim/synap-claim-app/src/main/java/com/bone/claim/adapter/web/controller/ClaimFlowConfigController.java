package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.dto.request.FlowConfigReq;
import com.bone.claim.application.ClaimFlowConfigApplicationService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 决策图流程配置端点（M5 第一阶段）。
 *
 * <p><b>语义先说清</b>：{@code skipNodes} 只允许 {@code AiAssess}；跳过 AI 评估后决策结论 一律 {@code
 * MANUAL_REQUIRED}（fail-closed 转人工），<b>不是</b>跳过 AI 直接自动通过。
 */
@Tag(name = "智能理赔-流程配置", description = "决策图可跳过节点的读取与保存")
@RestController
@RequestMapping("/api/v1/claim-config/flows")
@RequiredArgsConstructor
public class ClaimFlowConfigController {

  private final ClaimFlowConfigApplicationService flowConfigApplicationService;

  @Operation(summary = "读取决策图流程配置")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/decision-graph")
  public ApiResponse<ClaimFlowConfigApplicationService.FlowConfigView> current() {
    return ApiResponse.success(HttpStatus.OK.value(), flowConfigApplicationService.current());
  }

  @Operation(summary = "保存决策图流程配置", description = "仅允许 skipNodes=[AiAssess]；跳过即转人工")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description = "SC_CLAIM_FLOW_CONFIG_INVALID：跳过节点不在白名单 / 配置 JSON 无法序列化")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "SC_CLAIM_CONCURRENT_CONFLICT：@Version 乐观锁冲突")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/decision-graph")
  public ApiResponse<ClaimFlowConfigApplicationService.FlowConfigView> save(
      @Valid @RequestBody FlowConfigReq request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), flowConfigApplicationService.save(request.skipNodes()));
  }
}
