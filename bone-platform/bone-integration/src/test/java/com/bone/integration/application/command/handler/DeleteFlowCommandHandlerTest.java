package com.bone.integration.application.command.handler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.integration.application.command.cmd.DeleteFlowCommand;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteFlowCommandHandlerTest {

  @Mock IntegrationFlowRepository flowRepository;

  DeleteFlowCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeleteFlowCommandHandler(flowRepository);
  }

  @Test
  void deleteFlowCallsRepository() {
    DeleteFlowCommand cmd = new DeleteFlowCommand(5L);
    handler.handle(cmd);
    verify(flowRepository, times(1)).deleteById(5L);
  }
}
