package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiProviderProbeApplicationService;
import com.bone.claim.application.AiProviderProbeApplicationService.ProviderProbeReport;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型 provider 连通性探针（只读，一次最小真实调用）。
 *
 * <p><b>为什么单开一个端点</b>：{@code /ai-readiness} 是部署态（配没配）、{@code /ai-runtime-health} 是运行期（跑成什么样），
 * 都回答不了「刚刚改完配置，现在通了吗」。而这三件事的处置动作完全不同：配置态问题去改环境变量，连通态问题去换密钥 / 改超时，
 * 运行期问题去看业务流量与提示词。混在一个响应里，读者分不清哪个是事实、哪个是推论。
 *
 * <p><b>权限</b>复用 {@code claim:claims:read}，与 {@code /ai-runtime-health} 同口径：本仓已发生过「新造权限码未落 IAM 权限表
 * ⇒ 菜单可见但请求全 403」的事故，只读运维端点不再新造权限码。
 *
 * <p><b>注意副作用</b>：每次调用会真实消耗一次 provider 用量（场景标签 {@code claim-ai-provider-probe}）。刻意不做缓存——
 * 缓存会让「现在通不通」退化成「上次通不通」，而运维点它的动机就是前者。
 */
@Tag(name = "智能理赔-AI治理", description = "provider 连通性探针：配置了 ≠ 通了 ≠ 契约对")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class AiProviderProbeController {

  private final AiProviderProbeApplicationService aiProviderProbeApplicationService;

  @Operation(
      summary = "模型 provider 连通性探针",
      description =
          "发起一次最小化真实调用，返回判词（NOT_CONFIGURED / REACHABLE / 受控降级原因码）、模型标识、"
              + "模型来源（PROVIDER / STUB）、端到端耗时与该判词对应的处置动作。"
              + "未配置 base-url 时不发请求，直接返回 NOT_CONFIGURED；"
              + "已配置但返回的不是合法 JSON 记 OUTPUT_CONTRACT_VIOLATION（链路通、契约不通），"
              + "拿到网关替身响应记 MOCK_NOT_ALLOWED（配了但没生效）。"
              + "本端点只证明链路可用，不证明判断准确。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai-provider-probe")
  public ApiResponse<ProviderProbeReport> probe() {
    return ApiResponse.success(aiProviderProbeApplicationService.probe());
  }
}
