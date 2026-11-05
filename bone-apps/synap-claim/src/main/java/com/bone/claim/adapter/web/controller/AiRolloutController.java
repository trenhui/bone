package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiRolloutApplicationService;
import com.bone.claim.application.AiRolloutApplicationService.RolloutSnapshot;
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
 * AI 放量阶段与错误预算（只读）：回答「现在放量到哪一档、还差什么才能再放大」。
 *
 * <p><b>为什么要有这个端点</b>：准入闸的拒绝原因码（{@code AI_STAGE_SHADOW} / {@code AI_CANARY_MISS} / {@code
 * AI_BUDGET_EXCEEDED_AUTO_ROLLBACK}）只会写进案件日志，运维在页面看不到；没有这个出口，「为什么案件都转人工」
 * 就只能靠翻日志猜。三个口径分工：本端点回答<b>放量档位与预算</b>，{@code /ai-readiness} 回答<b>配没配</b>， {@code
 * /ai-runtime-health} 回答<b>跑没跑</b>，{@code /claims/eval/samples} 回答<b>判得准不准</b>。
 *
 * <p><b>只读</b>：不提供 promote / rollback 写端点。原因不是省事——自动降档由准入路径在预算击穿时完成（有真实证据支撑）， 人工降档用既有的开关配置入口（置
 * {@code DISABLED} 即刻生效，无缓存），把「调档」收在一条路径上，避免出现 「页面点上去、准入逻辑不认」的双写分叉。
 *
 * <p><b>权限</b>复用 {@code claim:claims:read}（新造权限码不落 IAM 权限表会让端点对所有人 403），响应只含计数、比率与 受控原因码，不含任何案件标识。
 */
@Tag(name = "智能理赔-AI治理", description = "AI 放量阶段机与错误预算：档位 / 抽检口径错误率 / 晋升阻塞项（只读）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class AiRolloutController {

  private final AiRolloutApplicationService aiRolloutApplicationService;

  @Operation(
      summary = "AI 放量阶段与错误预算（只读）",
      description =
          "返回当前生效档位（SHADOW/ASSISTED/CANARY/AUTOMATED）、灰度比例、预算阈值，以及窗口内"
              + "「自动通过案件里已抽检并出结论」的样本数、差异数、抽检口径错误率与剩余预算比例。"
              + "错误率在无样本时返回 null（不是 0%）——「没抽过」与「抽了没错」是两件事；"
              + "样本量未达门槛时不会判定超限，但会阻止晋升（promotionBlockers）。"
              + "truncated=true 表示窗口内样本超过 500 行上界，此时错误率只代表被取回的子集。"
              + "时间窗最多 31 天，越界直接 400，不静默裁剪。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai-rollout")
  public ApiResponse<RolloutSnapshot> rollout(
      @Parameter(description = "窗口起点（含），ISO-8601，如 2026-11-01T00:00:00Z")
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE_TIME)
          Instant from,
      @Parameter(description = "窗口终点（不含）") @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME)
          Instant to) {
    return ApiResponse.success(aiRolloutApplicationService.snapshot(from, to));
  }
}
