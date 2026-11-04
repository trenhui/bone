package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiDecisionDisclosureService;
import com.bone.claim.domain.model.decision.AiDecisionDisclosure.Disclosure;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 参与决策告知（只读，金发〔2026〕8号 §21 / §22、PIPL 第 24 条）。
 *
 * <p>供核赔工作台与拒赔告知书取数：<b>显著标识 + AI 角色 + 人工最终决策 + 复核/申诉权利 + 可复现证据</b>。 端点纯只读，复用既有 {@code
 * claim:claims:read}，不新增权限点、不写库、不触发决策执行、不碰资金。
 *
 * <p>无执行记录或显式 traceId 跨案时返回 {@code UNDETERMINED} 披露而非 404/空： 「查不到」绝不能被消费方翻译成「AI 未参与」。
 */
@Tag(name = "智能理赔-AI参与决策告知", description = "只读披露：生成内容显著标识与可复现证据")
@RestController
@RequestMapping("/api/v1/claims/{claimId}/decision")
@RequiredArgsConstructor
public class AiDecisionDisclosureController {

  private final AiDecisionDisclosureService aiDecisionDisclosureService;

  @Operation(
      summary = "查询 AI 参与决策告知",
      description =
          "基于已落库执行与轨迹生成只读披露；三态判定（AI_ASSISTED/AI_UNAVAILABLE/UNDETERMINED）。"
              + "无执行记录或 traceId 跨案时返回 UNDETERMINED，不代表 AI 未参与。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/disclosure")
  public ApiResponse<Disclosure> disclose(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "追溯ID（可选；显式指定历史执行复现）")
          @RequestParam(value = "traceId", required = false)
          String traceId) {
    return ApiResponse.success(aiDecisionDisclosureService.disclose(claimId, traceId));
  }
}
