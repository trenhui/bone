package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiCapabilityReadinessService;
import com.bone.claim.application.AiCapabilityReadinessService.ReadinessSnapshot;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 治理只读端点：能力清单 / 分级管控 / 自动化就绪度（监管 §15、§17）。
 *
 * <p><b>为什么单独一个 GET</b>：此前「三道闸门」的事实散落在 {@code RuleCheckNodeHandler} 的 warn 日志、 {@code
 * DecisionGraphExecutor} 的降级分支与 {@code application.yml} 的缺省值里，运维与审计要回答 「为什么当前自动放款率恒为
 * 0」只能读源码。本端点把它变成可查询、可留痕的治理资产，且不引入任何写面： 无请求体、无路径参数、不调用能力业务方法、不改开关——因此不存在「查一次就触发一次筛查/改一次状态」的副作用。
 *
 * <p><b>权限</b>复用既有的 {@code claim:claims:read}（不新增权限点，避免跨模块改 IAM 种子）。
 */
@Tag(name = "智能理赔-AI治理", description = "监管 §15 应用清单 / §17 紧急停用：能力就绪度（只读）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class AiCapabilityReadinessController {

  private final AiCapabilityReadinessService aiCapabilityReadinessService;

  @Operation(
      summary = "AI 能力清单与自动化就绪度（只读）",
      description =
          "返回各出站适配器自报的部署态声明、自动放款总开关状态与受控阻断原因码。" + "仅描述部署态，不代表具体案件已执行该能力，也不构成放款许可；运行期证据见决策轨迹。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai-readiness")
  public ApiResponse<ReadinessSnapshot> readiness() {
    return ApiResponse.success(aiCapabilityReadinessService.snapshot());
  }
}
