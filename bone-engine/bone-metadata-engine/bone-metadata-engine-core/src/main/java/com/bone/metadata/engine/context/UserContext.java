package com.bone.metadata.engine.context;

import org.springframework.stereotype.Component;

/** 用户上下文，用于获取当前用户信息 */
@Component
public class UserContext {

  /** 获取当前用户ID 在实际项目中，这里应该从Spring Security或其他安全框架中获取当前登录用户ID */
  public String getCurrentUserId() {
    // 简单实现：返回默认用户ID，实际项目中需要从安全上下文获取
    return "system";
  }

  /** 检查当前用户是否为系统管理员 */
  public boolean isAdmin() {
    // 简单实现：默认不是管理员，实际项目中需要从安全上下文检查用户角色
    String currentUserId = getCurrentUserId();
    return "admin".equals(currentUserId) || "system".equals(currentUserId);
  }
}
