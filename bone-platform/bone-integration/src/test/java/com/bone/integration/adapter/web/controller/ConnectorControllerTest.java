package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.CreateConnectorCmd;
import com.bone.integration.application.command.cmd.UpdateConnectorCmd;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorPageQry;
import com.bone.integration.application.usecase.standard.ConnectorPageQueryUseCase;
import com.bone.integration.application.usecase.standard.CreateConnectorUseCase;
import com.bone.integration.application.usecase.standard.UpdateConnectorUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorControllerTest {

    @Mock
    private CreateConnectorUseCase createConnectorUseCase;

    @Mock
    private UpdateConnectorUseCase updateConnectorUseCase;

    @Mock
    private ConnectorPageQueryUseCase connectorPageQueryUseCase;

    @InjectMocks
    private ConnectorController connectorController;

    @Test
    void create_delegatesToUseCase() {
        CreateConnectorCmd cmd = new CreateConnectorCmd("http-conn", "HTTP", Map.of("url", "http://localhost"));
        when(createConnectorUseCase.execute(cmd)).thenReturn(1L);

        ApiResponse<Long> response = connectorController.create(cmd);

        assertTrue(response.isSuccess());
        assertEquals(1L, response.getData());
    }

    @Test
    void update_delegatesToUseCase() {
        Long id = 1L;
        UpdateConnectorCmd cmd = new UpdateConnectorCmd(id, "updated", "HTTP", Map.of());

        ApiResponse<Void> response = connectorController.update(id, cmd);

        assertTrue(response.isSuccess());
        verify(updateConnectorUseCase).execute(new UpdateConnectorCmd(id, "updated", "HTTP", Map.of()));
    }

    @Test
    void page_returnsResult() {
        ConnectorPageQry qry = new ConnectorPageQry(1, 10, null, null, null);
        PageResult<ConnectorDTO> page = PageResult.of(Collections.emptyList(), 0L, 1, 10);
        when(connectorPageQueryUseCase.execute(qry)).thenReturn(page);

        ApiResponse<PageResult<ConnectorDTO>> response = connectorController.page(qry);

        assertTrue(response.isSuccess());
        assertEquals(page, response.getData());
    }
}
