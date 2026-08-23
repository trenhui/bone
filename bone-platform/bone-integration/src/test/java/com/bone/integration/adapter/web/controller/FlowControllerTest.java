package com.bone.integration.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.ActivateFlowCommand;
import com.bone.integration.application.command.cmd.CreateFlowCommand;
import com.bone.integration.application.command.handler.ActivateFlowCommandHandler;
import com.bone.integration.application.command.handler.CreateFlowHandler;
import com.bone.integration.application.command.handler.DeactivateFlowCommandHandler;
import com.bone.integration.application.command.handler.DeleteFlowCommandHandler;
import com.bone.integration.application.command.handler.UpdateFlowHandler;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.query.dto.FlowDTO;
import com.bone.integration.application.query.handler.FlowDetailQueryHandler;
import com.bone.integration.application.query.handler.FlowPageQueryHandler;
import com.bone.integration.application.query.qry.FlowPageQuery;
import com.bone.integration.application.service.FlowService;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowControllerTest {

  @Mock private CreateFlowHandler createFlowHandler;

  @Mock private UpdateFlowHandler updateFlowHandler;

  @Mock private FlowPageQueryHandler flowPageQueryHandler;

  @Mock private IntegrationFlowRepository flowRepository;

  @Mock private FlowService flowService;
  @Mock private FlowDetailQueryHandler flowDetailQueryHandler;
  @Mock private ActivateFlowCommandHandler activateFlowCommandHandler;
  @Mock private DeactivateFlowCommandHandler deactivateFlowCommandHandler;
  @Mock private DeleteFlowCommandHandler deleteFlowCommandHandler;

  @Mock private IntegrationDomainEventPublisher domainEventPublisher;

  @InjectMocks private FlowController flowController;

  @Test
  void create_delegatesToHandler() {
    CreateFlowCommand cmd = new CreateFlowCommand("flow-a", "desc", List.of(), List.of());
    when(createFlowHandler.handle(cmd)).thenReturn(1L);

    ApiResponse<Long> response = flowController.create(cmd);

    assertTrue(response.isSuccess());
    assertEquals(1L, response.getData());
  }

  @Test
  void page_returnsResult() {
    FlowPageQuery qry = new FlowPageQuery(1, 10, null, null);
    PageResult<FlowDTO> page = PageResult.of(Collections.emptyList(), 0L, 1, 10);
    when(flowPageQueryHandler.handle(qry)).thenReturn(page);

    ApiResponse<PageResult<FlowDTO>> response = flowController.page(qry);

    assertTrue(response.isSuccess());
    assertEquals(page, response.getData());
  }

  @Test
  void activate_updatesFlowStatus() {
    ApiResponse<Void> response = flowController.activate(2L);

    assertTrue(response.isSuccess());
    verify(activateFlowCommandHandler).handle(new ActivateFlowCommand(2L));
  }
}
