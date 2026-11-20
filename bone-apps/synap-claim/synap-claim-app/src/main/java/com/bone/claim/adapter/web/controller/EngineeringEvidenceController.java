package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.EngineeringEvidenceService;
import com.bone.claim.application.EngineeringEvidenceService.EvidenceView;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工程证据底座只读端点：把散落的 fail-closed 部署态证据收敛为可机检、可留痕的判据视图。
 *
 * <p><b>只读边界</b>：无请求体、无路径参数、不调用任何能力业务方法、不发 provider 请求、不改开关——只调用 {@link
 * EngineeringEvidenceService#view()} 聚合既有部署态声明。视图明确声明「证据齐备 ≠ 能力达标 ≠ 放款许可」， 不替代 L3 成熟度门禁。
 *
 * <p><b>权限</b>复用既有 {@code claim:claims:read}（不新增权限点，避免跨模块改 IAM 种子）。
 */
@Tag(name = "智能理赔-工程证据", description = "fail-closed 部署态证据视图（只读）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class EngineeringEvidenceController {

  private final EngineeringEvidenceService engineeringEvidenceService;

  @Operation(
      summary = "工程证据底座视图（只读）",
      description =
          "返回各 fail-closed 判据的部署态证据（稳定判据 ID / 受控状态 / 原因码）。"
              + "EVIDENCED 仅表示部署态有可核证据，不代表运行期达标，也不构成放款许可；不替代 L3 成熟度门禁。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/engineering-evidence")
  public ApiResponse<EvidenceView> evidence() {
    return ApiResponse.success(engineeringEvidenceService.view());
  }
}
