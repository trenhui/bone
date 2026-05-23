package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.CreateFlowCommand;
import com.bone.integration.application.command.cmd.UpdateFlowCommand;
import com.bone.integration.application.query.dto.FlowDTO;
import com.bone.integration.application.query.qry.FlowPageQuery;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.command.handler.CreateFlowHandler;
import com.bone.integration.application.query.handler.FlowPageQueryHandler;
import com.bone.integration.application.command.handler.UpdateFlowHandler;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.service.FlowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowControllerTest {

    @Mock
    private CreateFlowHandler createFlowHandler;

    @Mock
    private UpdateFlowHandler updateFlowHandler;

    @Mock
    private FlowPageQueryHandler flowPageQueryHandler;

    @Mock
    private IntegrationFlowRepository flowRepository;

    @Mock
    private FlowService flowService;

    @Mock
    private IntegrationDomainEventPublisher domainEventPublisher;

    @InjectMocks
    private FlowController flowController;

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
        IntegrationFlow flow = IntegrationFlow.create(2L, "n", "d");
        when(flowRepository.findById(2L)).thenReturn(flow);

        ApiResponse<Void> response = flowController.activate(2L);

        assertTrue(response.isSuccess());
        verify(flowRepository).save(flow);
        verify(domainEventPublisher).publishFrom(flow);
    }
}
