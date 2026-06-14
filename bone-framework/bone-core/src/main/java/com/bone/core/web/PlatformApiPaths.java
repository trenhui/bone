package com.bone.core.web;

/**
 * 平台 REST 路径唯一前缀（见 doc/architecture/Bone-API-规范.md §2、§13.2）。
 *
 * <p>集成服务 {@code server.servlet.context-path=/api} 时，Controller 使用 {@link #INTEGRATION_V1} （完整 URL
 * 为 {@code /api/v1/integration/**}）。
 */
public final class PlatformApiPaths {

  public static final String IAM_V1 = "/api/v1/iam";

  public static final String MASTERDATA_V1 = "/api/v1/masterdata";

  public static final String SYSTEM_V1 = "/api/v1/system";

  public static final String CONSOLE_V1 = "/api/v1/console";

  /** 相对 context-path {@code /api} 的集成域路径 */
  public static final String INTEGRATION_V1 = "/v1/integration";

  public static final String METADATA_V1 = "/api/v1/metadata";

  private PlatformApiPaths() {}
}
