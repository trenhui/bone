package com.bone.metadata.engine.runtime.security;

import java.util.ArrayList;
import java.util.List;

/** 默认的CustomAuthentication实现 用于在没有外部认证系统时提供基本的认证功能 */
public class DefaultCustomAuthentication implements CustomAuthentication {
  private final String username;
  private final List<String> authorities;

  public DefaultCustomAuthentication(String username, List<String> authorities) {
    this.username = username;
    this.authorities = authorities != null ? new ArrayList<>(authorities) : new ArrayList<>();
  }

  @Override
  public String getName() {
    return username;
  }

  @Override
  public List<String> getAuthorities() {
    return new ArrayList<>(authorities);
  }

  @Override
  public boolean hasRole(String role) {
    if (role == null) {
      return false;
    }
    // 检查精确匹配和带ROLE_前缀的匹配
    return authorities.contains(role) || authorities.contains("ROLE_" + role);
  }

  /** 创建管理员认证对象的静态工厂方法 */
  public static DefaultCustomAuthentication createAdmin() {
    List<String> adminAuthorities = new ArrayList<>();
    adminAuthorities.add("ROLE_ADMIN");
    adminAuthorities.add("ADMIN");
    return new DefaultCustomAuthentication("admin", adminAuthorities);
  }

  /** 创建普通用户认证对象的静态工厂方法 */
  public static DefaultCustomAuthentication createUser(String username, List<String> roles) {
    List<String> authorities = new ArrayList<>();
    if (roles != null) {
      for (String role : roles) {
        authorities.add(role);
        if (!role.startsWith("ROLE_")) {
          authorities.add("ROLE_" + role);
        }
      }
    }
    return new DefaultCustomAuthentication(username, authorities);
  }
}
