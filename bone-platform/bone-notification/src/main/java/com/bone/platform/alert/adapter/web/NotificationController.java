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

/**
 * 站内信控制器。
 *
 * <p><b>{@code userId} 一律不出现在签名里（2026-10-03 修正 IDOR）</b>：本控制器原先把 {@code userId} 作为
 * {@code @RequestParam} 暴露给调用方，而服务层的归属校验 {@code message.getUserId().equals(userId)} 正是拿这个入参比对 ——
 * 调用方传自己的 ID 就能读/标记他人的站内信，校验形同虚设。 现全部改为由 {@link NotificationApplicationService} 从 JWT 主体解析，调用方无从指定。
 *
 * <p>因此这些端点<b>不需要 {@code @PreAuthorize} 权限码</b>：它们是"当前登录用户读自己的消息"， 授权对象就是 JWT 主体本身，归属校验已在服务层完成。门禁
 * {@code check-controller-authorization.py} 的豁免清单已按此理由登记。
 */
@RestController
@RequestMapping(PlatformApiPaths.NOTIFICATION_V1 + "/messages")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationApplicationService notificationApplicationService;

  @GetMapping
  public ApiResponse<List<NotificationMessage>> list(@RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.success(notificationApplicationService.listByCurrentUser(limit));
  }

  @GetMapping("/unread-count")
  public ApiResponse<Long> unreadCount() {
    return ApiResponse.success(notificationApplicationService.unreadCountByCurrentUser());
  }

  @GetMapping("/summary")
  public ApiResponse<Map<String, Object>> summary() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("unreadCount", notificationApplicationService.unreadCountByCurrentUser());
    result.put("messages", notificationApplicationService.listByCurrentUser(10));
    return ApiResponse.success(result);
  }

  @PostMapping("/{id}/read")
  public ApiResponse<Void> markRead(@PathVariable Long id) {
    notificationApplicationService.markRead(id);
    return ApiResponse.success();
  }
}
