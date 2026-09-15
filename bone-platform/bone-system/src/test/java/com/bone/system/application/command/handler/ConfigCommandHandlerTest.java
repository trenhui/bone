package com.bone.system.application.command.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.system.application.command.cmd.CreateConfigCommand;
import com.bone.system.application.query.ConfigUniquenessQuery;
import com.bone.system.domain.config.SystemConfig;
import com.bone.system.domain.model.config.vo.ConfigKey;
import com.bone.system.domain.model.config.vo.ConfigType;
import com.bone.system.domain.model.config.vo.ConfigValue;
import com.bone.system.domain.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigCommandHandlerTest {

  @Mock SystemConfigRepository systemConfigRepository;
  @Mock ConfigUniquenessQuery configUniquenessQuery;

  ConfigCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ConfigCommandHandler(systemConfigRepository, configUniquenessQuery);
  }

  @Test
  void createConfigSuccessfully() {
    CreateConfigCommand cmd = new CreateConfigCommand();
    cmd.setConfigKey("site.title");
    cmd.setConfigValue("Bone Platform");
    cmd.setConfigType("SYSTEM");
    cmd.setDescription("Site title");
    cmd.setEncrypted(false);

    when(configUniquenessQuery.existsByConfigKey(any(ConfigKey.class))).thenReturn(false);

    handler.handle(cmd);

    verify(systemConfigRepository, times(1)).save(any(SystemConfig.class));
  }

  @Test
  void duplicateConfigKeyThrows() {
    CreateConfigCommand cmd = new CreateConfigCommand();
    cmd.setConfigKey("site.title");
    cmd.setConfigValue("Bone");
    cmd.setConfigType("SYSTEM");

    when(configUniquenessQuery.existsByConfigKey(any(ConfigKey.class))).thenReturn(true);

    assertThatThrownBy(() -> handler.handle(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("配置键已存在");

    verify(systemConfigRepository, never()).save(any());
  }

  @Test
  void deleteExistingConfigCallsRepository() {
    SystemConfig config =
        SystemConfig.create(
            1L,
            ConfigKey.of("site.title"),
            ConfigValue.of("Bone"),
            "desc",
            ConfigType.SYSTEM,
            false);
    when(systemConfigRepository.findById(1L)).thenReturn(config);

    handler.delete(1L);

    verify(systemConfigRepository, times(1)).deleteById(1L);
  }

  @Test
  void deleteNonExistentConfigDoesNothing() {
    when(systemConfigRepository.findById(999L)).thenReturn(null);

    handler.delete(999L);

    verify(systemConfigRepository, never()).deleteById(any());
  }
}
