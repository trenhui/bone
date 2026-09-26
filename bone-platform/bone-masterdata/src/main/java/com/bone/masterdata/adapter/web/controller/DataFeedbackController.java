package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.FeedbackApplicationService;
import com.bone.masterdata.application.command.SubmitFeedbackCommand;
import com.bone.masterdata.domain.model.feedback.DataFeedback;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 下游反馈控制器（G17 / UC-C4）。 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/feedbacks")
@RequiredArgsConstructor
public class DataFeedbackController {

  private final FeedbackApplicationService feedbackService;

  @PreAuthorize("hasAuthority('masterdata:subscriptions:write')")
  @PostMapping
  public ApiResponse<Long> submit(@Valid @RequestBody SubmitFeedbackCommand cmd) {
    return ApiResponse.success(feedbackService.submit(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{id}/accept")
  public ApiResponse<Void> accept(@PathVariable Long id) {
    feedbackService.accept(id);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{id}/reject")
  public ApiResponse<Void> reject(
      @PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
    feedbackService.reject(id, body == null ? null : body.get("result"));
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{id}/complete")
  public ApiResponse<Void> complete(
      @PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
    feedbackService.complete(id, body == null ? null : body.get("result"));
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<List<DataFeedback>> byEntity(
      @RequestParam Long masterDataEntityId, @RequestParam(required = false) String status) {
    return ApiResponse.success(feedbackService.byEntity(masterDataEntityId, status));
  }
}
