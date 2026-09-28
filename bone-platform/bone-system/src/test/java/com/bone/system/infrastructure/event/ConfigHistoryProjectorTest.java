package com.bone.system.infrastructure.event;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.event.ConfigChangedEvent;
import com.bone.system.domain.model.config.event.ConfigCreatedEvent;
import com.bone.system.domain.model.config.valueobject.ConfigKey;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import com.bone.system.domain.model.config.valueobject.ConfigValue;
import com.bone.system.domain.repository.ConfigHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ConfigHistoryProjector} 单测：验证事件订阅器把变更投影为 ConfigHistory 落库。 */
@ExtendWith(MockitoExtension.class)
class ConfigHistoryProjectorTest {

  @Mock ConfigHistoryRepository configHistoryRepository;

  @InjectMocks ConfigHistoryProjector projector;

  @Test
  void onCreatedWritesCreateHistory() {
    SystemConfig config =
        SystemConfig.create(
            1L,
            ConfigKey.of("site.title"),
            ConfigValue.of("Bone Platform"),
            "desc",
            ConfigType.SYSTEM,
            false);
    ConfigCreatedEvent event = (ConfigCreatedEvent) config.getDomainEvents().get(0);

    projector.onCreated(event);

    verify(configHistoryRepository, times(1))
        .save(
            argThat(
                h ->
                    "CREATE".equals(h.getChangeType())
                        && h.getOldValue() == null
                        && "Bone Platform".equals(h.getNewValue())));
  }

  @Test
  void onChangedWritesUpdateHistory() {
    ConfigChangedEvent event =
        new ConfigChangedEvent(1L, "site.title", "Bone", "new-value", "admin");

    projector.onChanged(event);

    verify(configHistoryRepository, times(1))
        .save(
            argThat(
                h ->
                    "UPDATE".equals(h.getChangeType())
                        && "Bone".equals(h.getOldValue())
                        && "new-value".equals(h.getNewValue())
                        && "admin".equals(h.getOperator())));
  }
}
