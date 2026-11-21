package com.bone.procurement.contract.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.contract.application.AiDisclosureView;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.application.ContractReviewApplicationService;
import com.bone.procurement.contract.application.HumanOverrideInput;
import com.bone.procurement.contract.application.ReviewReport;
import com.bone.procurement.contract.application.SubmitReviewCommand;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 合同审查 HTTP 端点：写操作写权限、读操作读权限；能力开关关闭时不注册。 */
@RestController
@RequestMapping("/api/v1/procurement/contract-reviews")
@ConditionalOnContractAiReviewEnabled
public class ContractReviewController {

  private final ContractReviewApplicationService service;

  public ContractReviewController(ContractReviewApplicationService service) {
    this.service = service;
  }

  @PostMapping("")
  @PreAuthorize("hasAuthority('procurement:contract:review:write')")
  public ApiResponse<ReviewReport> submit(@Valid @RequestBody ContractReviewSubmitRequest request) {
    // walking skeleton：文档由 sourceRef 定位，真正的文件读取属 L3；此处不接收合同原文全文。
    SubmitReviewCommand command =
        new SubmitReviewCommand(
            request.contractNo(),
            new ByteArrayInputStream(new byte[0]),
            request.sourceRef(),
            request.docId(),
            request.idempotencyKey());
    return ApiResponse.success(service.submit(command));
  }

  @GetMapping("/{reviewId}")
  @PreAuthorize("hasAuthority('procurement:contract:review:read')")
  public ApiResponse<ReviewReport> getReport(@PathVariable("reviewId") String reviewId) {
    return ApiResponse.success(service.getReport(reviewId));
  }

  @GetMapping("/{reviewId}/disclosure")
  @PreAuthorize("hasAuthority('procurement:contract:review:read')")
  public ApiResponse<AiDisclosureView> disclosure(@PathVariable("reviewId") String reviewId) {
    return ApiResponse.success(service.disclosure(reviewId));
  }

  @PostMapping("/{reviewId}/override")
  @PreAuthorize("hasAuthority('procurement:contract:review:write')")
  public ApiResponse<ReviewReport> override(
      @PathVariable("reviewId") String reviewId, @Valid @RequestBody HumanOverrideInput input) {
    return ApiResponse.success(service.override(reviewId, input));
  }
}
