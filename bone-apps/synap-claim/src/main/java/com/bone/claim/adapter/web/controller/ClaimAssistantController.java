package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.ClaimAssistantApplicationService;
import com.bone.claim.application.ClaimAssistantApplicationService.AssistantReply;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能理赔助手（自然语言入口，WS-B P1）。
 *
 * <p>权限复用 {@code claim:claims:read}（只读问答，不扩大授权面，与 {@link AiEvalSampleIndexController} 同原则）。 AI
 * 网关未配置时端点仍可用，但答复如实标记「未就绪」，不编造条款或金额。
 */
@Tag(name = "智能理赔-AI助手", description = "自然语言问答入口（受控、降级诚实）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class ClaimAssistantController {

  private final ClaimAssistantApplicationService assistantService;

  @Operation(
      summary = "智能理赔助手问答",
      description = "以自然语言提问理赔政策 / 材料 / 流程；由 AI 网关生成答复。网关未配置时诚实返回未就绪，不编造条款或金额。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @PostMapping("/claim-assistant")
  public ApiResponse<AssistantReply> ask(@RequestBody AssistantRequest request) {
    return ApiResponse.success(assistantService.assist(request.question(), request.claimId()));
  }

  /** 助手请求体：question=自然语言问题；claimId=可选，关联具体赔案上下文。 */
  public record AssistantRequest(String question, String claimId) {}
}
