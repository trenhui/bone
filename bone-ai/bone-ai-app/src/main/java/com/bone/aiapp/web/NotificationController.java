package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiapp.config.BoneAiProperties;
import com.bone.ainotification.application.AlertNotificationApplicationService;
import com.bone.ainotification.domain.model.AlertEvent;
import com.bone.ainotification.domain.model.NotificationAck;
import com.bone.ainotification.domain.model.NotificationResult;
import com.bone.core.model.ApiResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产拓扑：消警闭环（触发→外发→ack 回写）HTTP 能力。
 *
 * <p>租户一律从上下文取（详设 §3.1：禁止入参传租户）。
 */
@RestController
@RequestMapping("/api/ai/v1/notify")
public class NotificationController {

  private final AlertNotificationApplicationService notificationService;
  private final AiTenantContext tenantContext;
  private final BoneAiProperties props;

  public NotificationController(
      AlertNotificationApplicationService notificationService,
      AiTenantContext tenantContext,
      BoneAiProperties props) {
    this.notificationService = notificationService;
    this.tenantContext = tenantContext;
    this.props = props;
  }

  @PostMapping("/dispatch")
  public ApiResponse<NotificationResult> dispatch(@RequestBody NotifyDispatchRequest req) {
    AlertEvent event =
        new AlertEvent(
            req.alertId(),
            tenantContext.requireNumericTenantId(),
            req.severity(),
            req.title(),
            req.body(),
            req.sourceModule(),
            Instant.now());
    String target = req.target() != null ? req.target() : props.getNotificationWebhookUrl();
    return ApiResponse.success(notificationService.dispatch(event, req.channel(), target));
  }

  @PostMapping("/ack")
  public ApiResponse<Void> ack(@RequestBody NotifyAckRequest req) {
    notificationService.acknowledge(
        new NotificationAck(
            req.alertId(),
            tenantContext.requireNumericTenantId(),
            req.ackedBy(),
            Instant.now(),
            req.note()));
    return ApiResponse.success();
  }
}
