package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiEvalSampleIndexApplicationService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DIFF 样本事件的运行时索引（只读）。
 *
 * <p>用途：验证「后督 DIFF → 评测样本事件」这条链路在<b>当前进程</b>里确实被真实消费过。 与 {@code GET
 * /api/v1/claims/eval/diff-samples} 分工明确：那边读已落库的抽检单 （权威事实源、重启不丢），这边读进程内易失索引（重启即清空）。
 *
 * <p><b>权限</b>：复用 {@code claim:claims:read}——新造权限码不落 IAM 表会让端点对所有人
 * 403（菜单可见、请求全拒），本仓已发生过。端点只读，不扩大授权面。
 */
@Tag(name = "智能理赔-AI治理", description = "DIFF 样本事件运行时索引（只读）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class AiEvalSampleIndexController {

  private final AiEvalSampleIndexApplicationService indexService;

  @Operation(
      summary = "查询 DIFF 样本事件运行时索引",
      description =
          "返回本进程内已被 @EventListener 真实消费的 DIFF 样本事件数（按 fingerprint 幂等去重）"
              + "与只读快照。这是进程内易失观测值，进程重启即清空、多实例各持一份；"
              + "可回溯/可审计的权威事实源是已落库的 t_claim_inspection，请改用 "
              + "GET /api/v1/claims/eval/diff-samples。本端点不产出评测集、盲标金标准或任何准确率数值。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai-eval-sample-index")
  public ApiResponse<AiEvalSampleIndexApplicationService.Report> index() {
    return ApiResponse.success(indexService.report());
  }
}
