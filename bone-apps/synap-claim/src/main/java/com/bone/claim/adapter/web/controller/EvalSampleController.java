package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.EvalSampleExportApplicationService;
import com.bone.claim.application.EvalSampleExportApplicationService.EvalSampleExport;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测样本导出（AI 原生能力里「评测侧消费」的唯一出口）。
 *
 * <p><b>为什么不放在 {@code DecisionController} 下</b>：决策端点按案件维度（{@code /claims/{claimId}/...}），
 * 评测导出是<b>跨案件的时间窗查询</b>，挂到案件路径上会误导调用方以为「只能查一个案子」。
 *
 * <p><b>为什么复用 {@code claim:claims:read}</b>：新造 {@code claim:eval:read} 而不落权限表，会让端点对所有人
 * 403（菜单可见、请求全拒），这类「新码不落库」的事故本仓已发生过。评测导出只读、且不含直接标识字段， 用既有案件读权限即可，不额外扩大授权面。
 */
@Tag(name = "智能理赔-评测样本", description = "AI 认知与人工终态的弱标签样本导出（eval-only）")
@RestController
@RequestMapping("/api/v1/claims/eval")
@RequiredArgsConstructor
public class EvalSampleController {

  private final EvalSampleExportApplicationService evalSampleExportApplicationService;

  @Operation(
      summary = "导出评测样本",
      description =
          "按决策收敛时间窗导出「AI 建议 + 案件最终状态」的弱标签样本。"
              + "标签来自案件终态（FINISHED / CLOSED_REJECTED），不是独立盲标金标准；"
              + "AI 转人工/补件的样本标记为 UNKNOWN，不计入准确率。"
              + "时间窗最多 31 天、单次最多 500 条——越界直接 400，不静默裁剪。"
              + "返回体不含报案号与被保人标识，仅供 eval-only 使用。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/samples")
  public ApiResponse<EvalSampleExport> samples(
      @Parameter(description = "窗口起点（含），ISO-8601，如 2026-11-01T00:00:00Z")
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE_TIME)
          Instant from,
      @Parameter(description = "窗口终点（不含）") @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME)
          Instant to,
      @Parameter(description = "期望条数 1..500；实际可能少于请求值") @RequestParam(defaultValue = "100")
          int limit) {
    return ApiResponse.success(evalSampleExportApplicationService.export(from, to, limit));
  }
}
