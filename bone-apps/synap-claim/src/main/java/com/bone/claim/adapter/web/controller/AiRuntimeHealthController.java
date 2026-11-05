package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiRuntimeHealthApplicationService;
import com.bone.claim.application.AiRuntimeHealthApplicationService.AiRuntimeHealth;
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
 * AI 运行期健康度（只读）：回答「AI 现在在不在线」。
 *
 * <p><b>为什么不并进 {@code /ai-readiness}</b>：那个端点回答的是<b>部署态</b>（配没配、开没开），不查库、不受时间窗影响；
 * 本端点回答<b>运行期</b>（这段时间实际跑了几次、降了几次、为什么），必须带时间窗且要读轨迹。合并会让「已配置」和 「正在降级」混在同一个响应里，读者分不清哪个是配置事实、哪个是运行事实。
 *
 * <p><b>权限</b>复用既有的 {@code claim:claims:read}：新造权限码不落 IAM 权限表会让端点对所有人 403（菜单可见、请求全拒），
 * 本仓已发生过这类事故；本端点只读且返回的是计数与受控原因码，不含案件标识。
 */
@Tag(name = "智能理赔-AI治理", description = "AI 运行期健康度：参与率 / 降级归因 / 归因覆盖率（只读）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class AiRuntimeHealthController {

  private final AiRuntimeHealthApplicationService aiRuntimeHealthApplicationService;

  @Operation(
      summary = "AI 运行期健康度（只读）",
      description =
          "按节点落库时间窗聚合 AiAssess 节点：参与数 / 降级数 / 无法判定数、参与率、"
              + "降级原因码分布、归因覆盖率，以及能力缺失（知识/规则/视觉/图计算）的原因码分布。"
              + "参与率的分母只含「已明确表态」的节点，分母为 0 时返回 null（不是 0%）；"
              + "归因覆盖率与参与率分开，因为「跑了但说不清为什么降级」和「根本没跑」是两种状态。"
              + "两者都不是准确率——准确率见 /claims/eval/samples 的 agreementRate。"
              + "时间窗最多 31 天、单次最多 2000 个节点，越界直接 400，不静默裁剪。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai-runtime-health")
  public ApiResponse<AiRuntimeHealth> runtimeHealth(
      @Parameter(description = "窗口起点（含），ISO-8601，如 2026-11-01T00:00:00Z")
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE_TIME)
          Instant from,
      @Parameter(description = "窗口终点（不含）") @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME)
          Instant to) {
    return ApiResponse.success(aiRuntimeHealthApplicationService.health(from, to));
  }
}
