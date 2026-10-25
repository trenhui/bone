package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.EvidenceApplicationService;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A2 证据材料接口（挂在案件下的子资源）。
 *
 * <p>影像本体走平台附件服务 STS 直传（§7.2），本接口只登记 {@code fileRef} 与内容哈希—— 避免 20 份影像同步穿透打爆应用线程池。
 */
@Tag(name = "智能理赔-证据材料", description = "材料登记、解析回填、证据锁定")
@RestController
@RequestMapping("/api/v1/claims/{claimId}/evidences")
@RequiredArgsConstructor
public class EvidenceController {

  private final EvidenceApplicationService evidenceApplicationService;

  /** 材料登记请求。 */
  public record RegisterEvidenceReq(
      @NotBlank(message = "附件引用不能为空") String fileRef,
      @NotBlank(message = "内容哈希不能为空") String contentHash,
      DocType docType,
      String verifyValid) {}

  /** 解析回填请求（OCR/LLM 回调或人工补录）。 */
  public record ParseResultReq(
      OcrStatus ocrStatus, BigDecimal ocrConfidence, String extractedJson, String verifyValid) {}

  @Operation(summary = "登记材料", description = "登记已上传至附件服务的材料；同内容哈希重复登记返回 409")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "登记成功"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "SC_CLAIM_EVIDENCE_LIMIT_EXCEEDED: 单案件 ≤20 份"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "SC_CLAIM_EVIDENCE_DUPLICATED: 重复材料")
  })
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<Long> register(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Valid @RequestBody RegisterEvidenceReq request) {
    long id =
        evidenceApplicationService.register(
            new EvidenceApplicationService.RegisterEvidence(
                claimId,
                request.docType() == null ? DocType.INVOICE : request.docType(),
                request.fileRef(),
                request.contentHash(),
                request.verifyValid()));
    return ApiResponse.success(HttpStatus.CREATED.value(), id);
  }

  @Operation(summary = "回填解析结果", description = "OCR/LLM 解析回调；置信度 <0.7 自动置 MANUAL 转人工补录")
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/{evidenceId}/parse-result")
  public ApiResponse<Void> markParsed(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "材料ID") @PathVariable long evidenceId,
      @Valid @RequestBody ParseResultReq request) {
    evidenceApplicationService.markParsed(
        evidenceId,
        request.ocrStatus() == null ? OcrStatus.DONE : request.ocrStatus(),
        request.ocrConfidence(),
        request.extractedJson(),
        request.verifyValid());
    return ApiResponse.success();
  }

  @Operation(summary = "锁定证据集", description = "理算前置（§4.3）：锁定后材料不可改，保证理算可复算")
  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/lock")
  public ApiResponse<Integer> lock(@Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(
        evidenceApplicationService.lockAll(claimId, CurrentOperator.requireId()));
  }

  @Operation(summary = "材料列表", description = "案件下已登记材料（含解析状态与置信度）")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<List<Evidence>> list(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(evidenceApplicationService.listByClaim(claimId));
  }
}
