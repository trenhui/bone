package com.bone.system.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.system.application.command.CreateConfigCommand;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.valueobject.ConfigKey;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import com.bone.system.domain.model.config.valueobject.ConfigValue;
import com.bone.system.domain.repository.SystemConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 配置用例的应用层测试。
 *
 * <p>覆盖的是<b>应用层契约</b>：写路径「加载 → 行为 → 保存 → 发布」是否成对、失败是否翻译成正确错误码。 聚合内部不变量（配置键格式、状态迁移）由 {@code
 * SystemConfigTest} 用纯领域单测覆盖，这里不重复。
 */
@ExtendWith(MockitoExtension.class)
class ConfigApplicationServiceTest {

  @Mock SystemConfigRepository systemConfigRepository;
  @Mock com.bone.core.domain.event.DomainEventPublisher domainEventPublisher;

  ConfigApplicationService service;

  @BeforeEach
  void setUp() {
    service = new ConfigApplicationService(systemConfigRepository, domainEventPublisher);
  }

  @Test
  void createConfigSuccessfully() {
    when(systemConfigRepository.findByConfigKey(any(ConfigKey.class))).thenReturn(Optional.empty());

    service.create(createCommand("site.title", "SYSTEM"));

    verify(systemConfigRepository, times(1)).save(any(SystemConfig.class));
    // save 之后必须发布：缺这句事件会随聚合一起被丢弃，且编译期与单测都不会报错
    verify(domainEventPublisher, times(1)).publishFrom(any(SystemConfig.class));
  }

  @Test
  void duplicateConfigKeyThrowsConflict() {
    when(systemConfigRepository.findByConfigKey(any(ConfigKey.class)))
        .thenReturn(Optional.of(existingConfig()));

    assertThatThrownBy(() -> service.create(createCommand("site.title", "SYSTEM")))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SYS_CONFIG_KEY_CONFLICT");

    verify(systemConfigRepository, never()).save(any());
  }

  /**
   * 非法 configType 必须落到 400 而不是 500。
   *
   * <p>{@code ConfigType.fromString} 抛的是 {@code DomainException}——若不做应用层翻译，它会绕过 {@code
   * GlobalExceptionHandler} 的 BizException 分支被兜底成 500，用户输错一个字母就看到「服务器内部错误」。
   */
  @Test
  void invalidConfigTypeTranslatedToBadRequest() {
    CreateConfigCommand command = createCommand("site.title", "NOT_A_TYPE");

    assertThatThrownBy(() -> service.create(command))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SYS_CONFIG_TYPE_INVALID");
  }

  @Test
  void updateMissingConfigThrowsNotFound() {
    com.bone.system.application.command.UpdateConfigCommand command =
        new com.bone.system.application.command.UpdateConfigCommand();
    command.setId(404L);
    command.setConfigValue("v");
    when(systemConfigRepository.findById(404L)).thenReturn(null);

    assertThatThrownBy(() -> service.update(command))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SYS_CONFIG_NOT_FOUND");
  }

  /** 删除不先查：{@code deleteById} 自身幂等，多一次查询只会多一个竞态窗口（E-4.1）。 */
  @Test
  void deleteConfigDelegatesToRepository() {
    service.delete(1L);

    verify(systemConfigRepository, times(1)).deleteById(1L);
    verify(systemConfigRepository, never()).findById(any());
  }

  private static CreateConfigCommand createCommand(String key, String configType) {
    CreateConfigCommand command = new CreateConfigCommand();
    command.setConfigKey(key);
    command.setConfigValue("Bone Platform");
    command.setConfigType(configType);
    command.setDescription("Site title");
    command.setEncrypted(false);
    return command;
  }

  private static SystemConfig existingConfig() {
    return SystemConfig.create(
        1L, ConfigKey.of("site.title"), ConfigValue.of("Bone"), "desc", ConfigType.SYSTEM, false);
  }
}
