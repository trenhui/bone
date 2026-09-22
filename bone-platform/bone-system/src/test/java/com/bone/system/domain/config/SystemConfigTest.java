package com.bone.system.domain.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.event.ConfigChangedEvent;
import com.bone.system.domain.model.config.event.ConfigCreatedEvent;
import com.bone.system.domain.model.config.valueobject.ConfigKey;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import com.bone.system.domain.model.config.valueobject.ConfigValue;
import org.junit.jupiter.api.Test;

/** {@link SystemConfig} 纯单测：创建/改值/改描述的领域事件契约与键值约束（无容器）。 */
class SystemConfigTest {

  private SystemConfig createConfig() {
    return SystemConfig.create(
        1L, ConfigKey.of("sso.enabled"), ConfigValue.of("true"), "单点登录开关", ConfigType.SYSTEM, true);
  }

  @Test
  void testCreatePublishesCreatedEvent() {
    SystemConfig config = createConfig();

    assertEquals("sso.enabled", config.getConfigKey().value());
    assertEquals(ConfigType.SYSTEM, config.getConfigType());
    assertEquals(true, config.isEncrypted());
    assertEquals(1, config.getDomainEvents().size());
    assertInstanceOf(ConfigCreatedEvent.class, config.getDomainEvents().get(0));
  }

  @Test
  void testUpdateValuePublishesChangeEventWithOperator() {
    SystemConfig config = createConfig();
    config.clearDomainEvents();

    config.updateValue(ConfigValue.of("false"), "admin");

    assertEquals("false", config.getConfigValue().value());
    assertEquals(1, config.getDomainEvents().size());
    ConfigChangedEvent changed = (ConfigChangedEvent) config.getDomainEvents().get(0);
    assertEquals(1L, changed.configId());
    assertEquals("sso.enabled", changed.configKey());
    assertEquals("true", changed.oldValue());
    assertEquals("false", changed.newValue());
    assertEquals("admin", changed.operator());
  }

  @Test
  void testUpdateDescriptionOverridesDescriptionOnly() {
    SystemConfig config = createConfig();

    config.updateDescription("新版单点登录开关说明");

    assertEquals("新版单点登录开关说明", config.getDescription());
    // 改描述不触发变更事件，也不改动配置值
    assertEquals(1, config.getDomainEvents().size());
    assertEquals("true", config.getConfigValue().value());
  }

  @Test
  void testBlankKeyOrNullValueRejected() {
    assertThrows(DomainException.class, () -> ConfigKey.of(null));
    assertThrows(DomainException.class, () -> ConfigKey.of("   "));
    assertThrows(DomainException.class, () -> ConfigValue.of(null));
  }
}
