package com.bone.engine.extension.studio.security;

/**
 * OpenAPI Scope，与 Bone-API-规范 §9.2 一致。
 *
 * <p>5a 姊妹篇 G3 权限分层：新增 write/read/bind/observe/marketplace 五组细分 Scope； 过渡期各端点经 {@code hasAnyScope}
 * 同时接受旧 Scope（legacy-scope-fallback=true，默认）， 切流置 false 后仅认新
 * Scope。部署（plugins:deploy）与生效（runtime:publish）分权是 SoD 重点。
 */
public final class ExtensionScopes {

  public static final String POINTS_READ = "extension:points:read";
  public static final String POINTS_WRITE = "extension:points:write";

  public static final String PLUGINS_READ = "extension:plugins:read";
  public static final String PLUGINS_WRITE = "extension:plugins:write";
  public static final String PLUGINS_DEPLOY = "extension:plugins:deploy";
  public static final String PLUGINS_BIND = "extension:plugins:bind";

  /** 生效切换（publish-runtime / 回滚后的路由发布）：租户管理员动作，与部署分权（SoD） */
  public static final String RUNTIME_PUBLISH = "extension:runtime:publish";

  public static final String OBSERVE_READ = "extension:observe:read";
  public static final String MARKETPLACE_INSTALL = "extension:marketplace:install";
  public static final String MARKETPLACE_MANAGE = "extension:marketplace:manage";

  private ExtensionScopes() {}
}
