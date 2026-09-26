package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.SubscriptionApplicationService;
import com.bone.masterdata.application.command.RequestSubscriptionCommand;
import com.bone.masterdata.domain.model.subscription.EntitySubscription;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 主数据消费订阅控制器（G10）。 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/subscriptions")
@RequiredArgsConstructor
public class EntitySubscriptionController {

  private final SubscriptionApplicationService subscriptionService;

  @PreAuthorize("hasAuthority('masterdata:subscriptions:write')")
  @PostMapping
  public ApiResponse<Long> request(@Valid @RequestBody RequestSubscriptionCommand cmd) {
    return ApiResponse.success(subscriptionService.request(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{id}/approve")
  public ApiResponse<Void> approve(@PathVariable Long id) {
    subscriptionService.approve(id);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:subscriptions:write')")
  @PostMapping("/{id}/revoke")
  public ApiResponse<Void> revoke(@PathVariable Long id) {
    subscriptionService.revoke(id);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<List<EntitySubscription>> byEntity(@RequestParam Long masterDataEntityId) {
    return ApiResponse.success(subscriptionService.byEntity(masterDataEntityId));
  }
}
