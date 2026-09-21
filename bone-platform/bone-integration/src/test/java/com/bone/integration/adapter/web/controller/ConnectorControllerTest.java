package com.bone.integration.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.CreateConnectorCommand;
import com.bone.integration.application.command.cmd.UpdateConnectorCommand;
import com.bone.integration.application.command.handler.CreateConnectorApplicationService;
import com.bone.integration.application.command.handler.UpdateConnectorApplicationService;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.handler.ConnectorPageQueryApplicationService;
import com.bone.integration.application.query.qry.ConnectorPageQuery;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorControllerTest {

  @Mock private CreateConnectorApplicationService createConnectorHandler;

  @Mock private UpdateConnectorApplicationService updateConnectorHandler;

  @Mock private ConnectorPageQueryApplicationService connectorPageQueryHandler;

  @InjectMocks private ConnectorController connectorController;

  @Test
  void create_delegatesToHandler() {
    CreateConnectorCommand cmd =
        new CreateConnectorCommand("http-conn", "HTTP", Map.of("url", "http://localhost"));
    when(createConnectorHandler.handle(cmd)).thenReturn(1L);

    ApiResponse<Long> response = connectorController.create(cmd);

    assertTrue(response.isSuccess());
    assertEquals(1L, response.getData());
  }

  @Test
  void update_delegatesToHandler() {
    Long id = 1L;
    UpdateConnectorCommand cmd = new UpdateConnectorCommand(id, "updated", "HTTP", Map.of());

    ApiResponse<Void> response = connectorController.update(id, cmd);

    assertTrue(response.isSuccess());
    verify(updateConnectorHandler)
        .handle(new UpdateConnectorCommand(id, "updated", "HTTP", Map.of()));
  }

  @Test
  void page_returnsResult() {
    ConnectorPageQuery qry = new ConnectorPageQuery(1, 10, null, null, null);
    PageResult<ConnectorDTO> page = PageResult.of(Collections.emptyList(), 0L, 1, 10);
    when(connectorPageQueryHandler.handle(qry)).thenReturn(page);

    ApiResponse<PageResult<ConnectorDTO>> response = connectorController.page(qry);

    assertTrue(response.isSuccess());
    assertEquals(page, response.getData());
  }
}
