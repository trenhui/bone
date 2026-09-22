package com.bone.platform.alert.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.platform.alert.application.NotificationApplicationService;
import com.bone.platform.alert.domain.model.notification.NotificationMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 站内信控制器 */
@RestController
@RequestMapping(PlatformApiPaths.NOTIFICATION_V1 + "/messages")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationApplicationService notificationApplicationService;

  @GetMapping
  public ApiResponse<List<NotificationMessage>> list(
      @RequestParam Long userId, @RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.success(notificationApplicationService.listByUser(userId, limit));
  }

  @GetMapping("/unread-count")
  public ApiResponse<Long> unreadCount(@RequestParam Long userId) {
    return ApiResponse.success(notificationApplicationService.unreadCount(userId));
  }

  @GetMapping("/summary")
  public ApiResponse<Map<String, Object>> summary(@RequestParam Long userId) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("unreadCount", notificationApplicationService.unreadCount(userId));
    result.put("messages", notificationApplicationService.listByUser(userId, 10));
    return ApiResponse.success(result);
  }

  @PostMapping("/{id}/read")
  public ApiResponse<Void> markRead(@PathVariable Long id) {
    notificationApplicationService.markRead(id);
    return ApiResponse.success();
  }
}
