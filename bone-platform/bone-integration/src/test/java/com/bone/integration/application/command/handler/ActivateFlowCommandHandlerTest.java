package com.bone.integration.application.command.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.command.cmd.ActivateFlowCommand;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivateFlowCommandApplicationServiceTest {

  @Mock IntegrationFlowRepository flowRepository;
  @Mock IntegrationDomainEventPublisher domainEventPublisher;

  ActivateFlowCommandApplicationService handler;

  @BeforeEach
  void setUp() {
    handler = new ActivateFlowCommandApplicationService(flowRepository, domainEventPublisher);
  }

  @Test
  void activateFlowSuccessfully() {
    IntegrationFlow flow = IntegrationFlow.create(1L, "Test Flow", "{}");
    when(flowRepository.findById(1L)).thenReturn(flow);

    ActivateFlowCommand cmd = new ActivateFlowCommand(1L);
    handler.handle(cmd);

    verify(flowRepository, times(1)).save(flow);
    verify(domainEventPublisher, times(1)).publishFrom(flow);
  }

  @Test
  void activateNonExistentFlowThrows() {
    when(flowRepository.findById(999L)).thenReturn(null);

    ActivateFlowCommand cmd = new ActivateFlowCommand(999L);

    assertThatThrownBy(() -> handler.handle(cmd))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("流程不存在");
  }
}
