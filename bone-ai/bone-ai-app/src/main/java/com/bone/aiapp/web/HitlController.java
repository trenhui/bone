package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItem.Disposition;
import com.bone.aiorch.domain.hitl.ReasonType;
import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产拓扑：HITL 人工终审 REST 面（详设 §2.3 契约；确认台 / 产品侧消费，权限复用 bone-iam）。
 *
 * <p>端点与设计附录 A 对齐：待裁决列表 / 裁决（confirm / reject / modify / escalate）/ 处置链回放。 租户一律从上下文取； {@code
 * reject} / {@code modify} 必带 {@code reasonType}（受控字典，自由文本不进回流管道）。
 */
@RestController
@RequestMapping("/api/ai/v1/hitl")
public class HitlController {

  private final HitlApplicationService hitlService;
  private final AiTenantContext tenantContext;

  public HitlController(HitlApplicationService hitlService, AiTenantContext tenantContext) {
    this.hitlService = hitlService;
    this.tenantContext = tenantContext;
  }

  /** 待裁决列表（PENDING；按场景 / 风险过滤，供确认台按风险分派裁决人）。 */
  @GetMapping("/items")
  public ApiResponse<List<HitlItem>> items(
      @RequestParam(required = false) String scene,
      @RequestParam(required = false) String riskLevel) {
    return ApiResponse.success(
        hitlService.findPending(tenantContext.requireTenantId(), scene, riskLevel));
  }

  /** 人工裁决回写：confirm / reject / modify / escalate。 */
  @PostMapping("/items/{itemId}/act")
  public ApiResponse<HitlItem> act(
      @PathVariable String itemId, @RequestBody HitlActRequest request) {
    String tenantId = tenantContext.requireTenantId();
    ReasonType reasonType =
        request.reasonType() == null || request.reasonType().isBlank()
            ? null
            : ReasonType.valueOf(request.reasonType());
    HitlItem item;
    switch (request.action()) {
      case "confirm" -> item = hitlService.confirm(tenantId, itemId, request.actor(), reasonType);
      case "reject" -> item =
          hitlService.reject(tenantId, itemId, request.actor(), reasonType, request.comment());
      case "modify" -> item =
          hitlService.modify(
              tenantId, itemId, request.actor(), request.value(), reasonType, request.comment());
      case "escalate" -> item =
          hitlService.escalate(tenantId, itemId, request.actor(), request.comment());
      default -> throw new BizException(
          422, "unknown action: " + request.action(), "AI_HITL_INVALID_TRANSITION");
    }
    return ApiResponse.success(item);
  }

  /** 处置链回放（谁在何时做了什么裁决、带什么原因——读持久化，跨请求可回放）。 */
  @GetMapping("/items/{itemId}/dispositions")
  public ApiResponse<List<Disposition>> dispositions(@PathVariable String itemId) {
    return ApiResponse.success(
        hitlService.dispositionHistory(tenantContext.requireTenantId(), itemId));
  }

  /** 详情 / 状态轮询（含终态项——确认台打开历史工单）。 */
  @GetMapping("/items/{itemId}")
  public ApiResponse<HitlItem> item(@PathVariable String itemId) {
    return ApiResponse.success(hitlService.findById(tenantContext.requireTenantId(), itemId));
  }

  /** 裁决请求（action 受控：confirm / reject / modify / escalate）。 */
  public record HitlActRequest(
      String action, String actor, String value, String reasonType, String comment) {}
}
