package com.bone.aiapp.web;

import com.bone.aiapp.config.BoneAiProperties;
import com.bone.ainotification.application.AlertNotificationService;
import com.bone.ainotification.domain.model.AlertEvent;
import com.bone.ainotification.domain.model.NotificationAck;
import com.bone.ainotification.domain.model.NotificationResult;
import com.bone.core.model.ApiResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 生产拓扑：消警闭环（触发→外发→ack 回写）HTTP 能力。 */
@RestController
@RequestMapping("/api/ai/v1/notify")
public class NotificationController {

  private final AlertNotificationService notificationService;
  private final BoneAiProperties props;

  public NotificationController(
      AlertNotificationService notificationService, BoneAiProperties props) {
    this.notificationService = notificationService;
    this.props = props;
  }

  @PostMapping("/dispatch")
  public ApiResponse<NotificationResult> dispatch(@RequestBody NotifyDispatchRequest req) {
    AlertEvent event =
        new AlertEvent(
            req.alertId(),
            req.tenantId(),
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
            req.alertId(), req.tenantId(), req.ackedBy(), Instant.now(), req.note()));
    return ApiResponse.success();
  }
}
