package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.assembler.ClaimSignAssembler;
import com.bone.claim.adapter.web.dto.request.ClaimSignRequests.AcknowledgeSignReq;
import com.bone.claim.adapter.web.dto.request.ClaimSignRequests.ConfirmSignReq;
import com.bone.claim.adapter.web.dto.request.ClaimSignRequests.CreateSignBatchReq;
import com.bone.claim.adapter.web.dto.request.ClaimSignRequests.ImportParticipantsReq;
import com.bone.claim.adapter.web.dto.response.SignBatchResp;
import com.bone.claim.adapter.web.dto.response.SignTrackResp;
import com.bone.claim.application.ClaimSignApplicationService;
import com.bone.claim.application.ClaimSignApplicationService.AutoSignResult;
import com.bone.claim.application.ClaimSignApplicationService.CreateOfflineBatchCommand;
import com.bone.claim.domain.model.sign.ClaimSignBatch;
import com.bone.claim.domain.model.sign.valueobject.SignChannel;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 案件签收端点：线上自动签收回执 + 线下批次签收。 */
@RestController
@RequestMapping("/api/v1/claims/sign")
@RequiredArgsConstructor
@Tag(name = "案件签收", description = "线上自动签收与线下人工批次签收")
public class ClaimSignController {

  private final ClaimSignApplicationService signService;

  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping({"", "/"})
  public ApiResponse<AutoSignResp> acknowledge(
      @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = true)
          String idempotencyKey,
      @Valid @RequestBody AcknowledgeSignReq req) {
    checkIdempotencyKey(idempotencyKey);
    AutoSignResult result =
        signService.signOnAcceptance(
            new ClaimSignApplicationService.AutoSignCommand(
                req.businessKey(), SignChannel.ofCode(req.signChannel())));
    return ApiResponse.success(
        new AutoSignResp(result.batchNo(), result.batchId(), result.created()));
  }

  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/batches")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Long> createBatch(@Valid @RequestBody CreateSignBatchReq req) {
    long batchId =
        signService.createOfflineBatch(
            new CreateOfflineBatchCommand(
                SignChannel.ofCode(req.signChannel()),
                req.businessKey(),
                req.expressCompany(),
                req.expressNo(),
                req.sender(),
                req.arriveTime(),
                CurrentOperator.requireId()));
    return ApiResponse.success(HttpStatus.CREATED.value(), batchId);
  }

  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/batches/{batchId}/participants")
  public ApiResponse<Void> importParticipants(
      @PathVariable long batchId, @Valid @RequestBody ImportParticipantsReq req) {
    signService.importParticipants(batchId, req.claimIds(), CurrentOperator.requireId());
    return ApiResponse.success(null);
  }

  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/batches/{batchId}/image")
  public ApiResponse<Void> registerImage(@PathVariable long batchId) {
    signService.registerImage(batchId, CurrentOperator.requireId());
    return ApiResponse.success(null);
  }

  @PreAuthorize("hasAuthority('claim:claims:write')")
  @PostMapping("/batches/{batchId}/confirm")
  public ApiResponse<Void> confirm(
      @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = true)
          String idempotencyKey,
      @PathVariable long batchId,
      @Valid @RequestBody ConfirmSignReq req) {
    checkIdempotencyKey(idempotencyKey);
    signService.confirmOffline(batchId, CurrentOperator.requireId(), req.remark());
    return ApiResponse.success(null);
  }

  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/batches")
  public ApiResponse<PageResult<SignBatchResp>> list(
      @ModelAttribute com.bone.claim.adapter.web.dto.request.ClaimSignPageReq req) {
    PageResult<ClaimSignBatch> page =
        signService.list(req.source(), req.signStatus(), req.pageOrDefault(), req.sizeOrDefault());
    return ApiResponse.success(page.map(batch -> ClaimSignAssembler.INSTANCE.toResp(batch)));
  }

  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/batches/{batchId}/tracks")
  public ApiResponse<java.util.List<SignTrackResp>> tracks(@PathVariable long batchId) {
    return ApiResponse.success(
        signService.tracks(batchId).stream().map(ClaimSignAssembler.INSTANCE::toResp).toList());
  }

  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/batches/{batchId}")
  public ApiResponse<SignBatchResp> detail(@PathVariable long batchId) {
    return ApiResponse.success(ClaimSignAssembler.INSTANCE.toResp(signService.detail(batchId)));
  }

  /** 幂等键长度上限对齐 DB varchar(64)：超长直接拒绝，避免真库写入时静默失败。 */
  private static void checkIdempotencyKey(String key) {
    if (key == null || key.isBlank() || key.length() > 64) {
      throw com.bone.claim.common.ClaimErrors.of(
          com.bone.claim.common.ClaimErrorCodes.SC_CLAIM_SIGN_PUSH_INVALID,
          "Idempotency-Key 必填且不超过64字符");
    }
  }

  public record AutoSignResp(String batchNo, long batchId, boolean created) {}
}
