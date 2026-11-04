package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.InvoiceVerificationApplicationService;
import com.bone.claim.application.port.out.InvoiceVerificationPort;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 发票验真 / 验重（材料阶段前置，结论落证据并进入既有规则链）。 */
@Tag(name = "发票验真", description = "材料阶段的票面要素核验与跨案去重")
@RestController
@RequestMapping("/api/v1/claims/{claimId}/evidences")
@RequiredArgsConstructor
public class InvoiceVerificationController {

  private final InvoiceVerificationApplicationService invoiceVerificationApplicationService;

  @Operation(
      summary = "核验单份材料关联的发票",
      description =
          "读取该材料的结构化解析结果，执行票面要素核验与跨案发票去重；结果落 t_claim_evidence.verify_valid。"
              + "一期无税局通道，本地核验通过返回 UNVERIFIABLE（转人工），只有查实问题才返回 FAIL。")
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/{evidenceId}/verify")
  public ApiResponse<InvoiceVerificationPort.VerificationResult> verify(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "材料ID") @PathVariable long evidenceId) {
    return ApiResponse.success(invoiceVerificationApplicationService.verify(evidenceId));
  }
}
