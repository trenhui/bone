package com.bone.engine.extension.studio.application.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.engine.extension.studio.domain.gateway.TenantDirectoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 5a 姊妹篇 G2：租户码校验规则（'*' 通配 / 存在性 / 遗留 DEFAULT 拒绝）。 */
class ExtensionValidationSupportTest {

  @Test
  @DisplayName("通配 * 合法")
  void wildcardAllowed() {
    assertDoesNotThrow(() -> ExtensionValidationSupport.assertTenantCodeValid("*", null));
  }

  @Test
  @DisplayName("空白放行（由领域默认值兜底）")
  void blankAllowed() {
    assertDoesNotThrow(() -> ExtensionValidationSupport.assertTenantCodeValid("  ", null));
  }

  @Test
  @DisplayName("遗留占位值 DEFAULT 拒绝（大小写不敏感）")
  void legacyDefaultRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ExtensionValidationSupport.assertTenantCodeValid("DEFAULT", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExtensionValidationSupport.assertTenantCodeValid("default", null));
  }

  @Test
  @DisplayName("格式非法拒绝")
  void badFormatRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ExtensionValidationSupport.assertTenantCodeValid("带空格 code", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExtensionValidationSupport.assertTenantCodeValid("#bad", null));
  }

  @Test
  @DisplayName("目录判定不存在则拒绝；目录不可用（null）降级放行")
  void directoryDecides() {
    TenantDirectoryPort missing = code -> Boolean.FALSE;
    TenantDirectoryPort unavailable = code -> null;
    TenantDirectoryPort present = code -> Boolean.TRUE;

    assertThrows(
        IllegalArgumentException.class,
        () -> ExtensionValidationSupport.assertTenantCodeValid("T1", missing));
    assertDoesNotThrow(() -> ExtensionValidationSupport.assertTenantCodeValid("T1", unavailable));
    assertDoesNotThrow(() -> ExtensionValidationSupport.assertTenantCodeValid("T1", present));
  }

  @Test
  @DisplayName("appId 须为正数或空")
  void appIdRules() {
    assertDoesNotThrow(() -> ExtensionValidationSupport.assertAppIdValid(null));
    assertDoesNotThrow(() -> ExtensionValidationSupport.assertAppIdValid(42L));
    assertThrows(
        IllegalArgumentException.class, () -> ExtensionValidationSupport.assertAppIdValid(0L));
    assertThrows(
        IllegalArgumentException.class, () -> ExtensionValidationSupport.assertAppIdValid(-1L));
  }
}
