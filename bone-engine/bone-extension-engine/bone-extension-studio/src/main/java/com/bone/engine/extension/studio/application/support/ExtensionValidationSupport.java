package com.bone.engine.extension.studio.application.support;

import com.bone.engine.extension.studio.domain.gateway.TenantDirectoryPort;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 扩展登记校验支撑（5a 姊妹篇 G1/G2）。
 *
 * <p>租户码规则：{@code *}（通配，默认实现兜底依赖）或平台真实租户编码； 遗留占位值 {@code DEFAULT} 不再接受（存量由迁移脚本 0002 映射为 {@code *}）。
 */
public final class ExtensionValidationSupport {

  private static final Pattern TENANT_CODE_PATTERN =
      Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$");

  private static final String WILDCARD = "*";

  private ExtensionValidationSupport() {}

  /**
   * @param tenantDirectory 可空：无目录时降级为格式校验（in-memory 联调场景）
   */
  public static void assertTenantCodeValid(String tenantCode, TenantDirectoryPort tenantDirectory) {
    if (!StringUtils.hasText(tenantCode)) {
      return;
    }
    String code = tenantCode.trim();
    if (WILDCARD.equals(code)) {
      return;
    }
    if ("DEFAULT".equalsIgnoreCase(code)) {
      throw new IllegalArgumentException("tenantCode 不接受遗留占位值 DEFAULT：请使用 *（通配）或租户编码");
    }
    if (!TENANT_CODE_PATTERN.matcher(code).matches()) {
      throw new IllegalArgumentException("tenantCode 格式非法: " + code);
    }
    if (tenantDirectory != null) {
      Boolean known = tenantDirectory.exists(code);
      if (Boolean.FALSE.equals(known)) {
        throw new IllegalArgumentException("租户编码不存在: " + code);
      }
    }
  }

  public static void assertAppIdValid(Long appId) {
    if (appId != null && appId <= 0) {
      throw new IllegalArgumentException("appId 须为正整数");
    }
  }
}
