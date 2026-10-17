package com.bone.aiserver.web;

import com.bone.aiorch.domain.hitl.ReasonType;
import com.bone.aiserver.claim.ClaimService;
import com.bone.aiserver.claim.ClaimService.ClaimDecision;
import com.bone.aiserver.claim.ClaimService.ClaimRequest;
import com.bone.core.model.ApiResponse;
import java.io.InputStream;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 智能理赔演示端点（参考宿主，产品对照接线）。
 *
 * <p>把理赔闭环（解析条款 → 规则短路 | LLM → 高风险人工终审）以 HTTP 暴露： 提交理赔单 + 保单 PDF，返回 {@link ClaimDecision} （含 IR 条款
 * / 短路信号 / HITL 待决）。人工终审经 {@code act} 端点回写（confirm/reject/modify，ReasonType 受控回流）。
 *
 * <p>多部分表单：{@code claimId} / {@code tenantId} / {@code patientDesc} / {@code amount} / {@code
 * riskLevel} / {@code policyPdf}。
 */
@RestController
@RequestMapping("/api/ai/v1/claim")
public class ClaimController {

  private final ClaimService claimService;

  public ClaimController(ClaimService claimService) {
    this.claimService = claimService;
  }

  @PostMapping(value = "/underwrite", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<ClaimDecision> underwrite(
      @RequestPart("tenantId") String tenantId,
      @RequestPart("claimId") String claimId,
      @RequestPart("patientDesc") String patientDesc,
      @RequestPart("amount") double amount,
      @RequestPart("riskLevel") String riskLevel,
      @RequestPart(value = "policyPdf", required = false) MultipartFile policyPdf) {
    InputStream pdf = policyPdf == null ? InputStream.nullInputStream() : safeStream(policyPdf);
    return ApiResponse.success(
        claimService.underwrite(
            new ClaimRequest(
                tenantId,
                claimId,
                patientDesc,
                amount,
                riskLevel,
                pdf,
                policyPdf == null ? claimId + ".pdf" : policyPdf.getOriginalFilename())));
  }

  /** 人工终审回写（confirm / reject / modify；reject/modify 必带 reasonType）。 */
  @PostMapping("/{itemId}/act")
  public ApiResponse<Object> act(
      @RequestPart("tenantId") String tenantId,
      @org.springframework.web.bind.annotation.PathVariable("itemId") String itemId,
      @RequestPart("actor") String actor,
      @RequestPart("action") String action,
      @RequestPart(value = "value", required = false) Object value,
      @RequestPart(value = "reasonType", required = false) String reasonType,
      @RequestPart(value = "comment", required = false) String comment) {
    ReasonType rt = reasonType == null ? null : ReasonType.valueOf(reasonType);
    return ApiResponse.success(
        claimService.act(tenantId, itemId, actor, action, value, rt, comment));
  }

  /** 处置链回放。 */
  @PostMapping("/{itemId}/history")
  public ApiResponse<List<com.bone.aiorch.domain.hitl.HitlItem.Disposition>> history(
      @RequestPart("tenantId") String tenantId,
      @org.springframework.web.bind.annotation.PathVariable("itemId") String itemId) {
    return ApiResponse.success(claimService.dispositionHistory(tenantId, itemId));
  }

  private static InputStream safeStream(MultipartFile file) {
    try {
      return file.getInputStream();
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("读取保单文件失败: " + e.getMessage(), e);
    }
  }
}
