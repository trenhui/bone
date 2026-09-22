package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.integration.application.ConnectorDetailQueryApplicationService;
import com.bone.integration.application.ConnectorPageQueryApplicationService;
import com.bone.integration.application.CreateConnectorApplicationService;
import com.bone.integration.application.DeleteConnectorApplicationService;
import com.bone.integration.application.DisableConnectorApplicationService;
import com.bone.integration.application.EnableConnectorApplicationService;
import com.bone.integration.application.TestConnectorApplicationService;
import com.bone.integration.application.UpdateConnectorApplicationService;
import com.bone.integration.application.command.cmd.CreateConnectorCommand;
import com.bone.integration.application.command.cmd.DeleteConnectorCommand;
import com.bone.integration.application.command.cmd.DisableConnectorCommand;
import com.bone.integration.application.command.cmd.EnableConnectorCommand;
import com.bone.integration.application.command.cmd.TestConnectorCommand;
import com.bone.integration.application.command.cmd.UpdateConnectorCommand;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorDetailQuery;
import com.bone.integration.application.query.qry.ConnectorPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.INTEGRATION_V1 + "/connectors")
@RequiredArgsConstructor
public class ConnectorController {
  private final CreateConnectorApplicationService createConnectorHandler;
  private final UpdateConnectorApplicationService updateConnectorHandler;
  private final ConnectorPageQueryApplicationService connectorPageQueryHandler;
  private final ConnectorDetailQueryApplicationService connectorDetailQueryHandler;
  private final DeleteConnectorApplicationService deleteConnectorHandler;
  private final TestConnectorApplicationService testConnectorHandler;
  private final EnableConnectorApplicationService enableConnectorHandler;
  private final DisableConnectorApplicationService disableConnectorHandler;

  @PostMapping
  public ApiResponse<Long> create(@RequestBody CreateConnectorCommand cmd) {
    Long id = createConnectorHandler.handle(cmd);
    return ApiResponse.success(id);
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateConnectorCommand cmd) {
    updateConnectorHandler.handle(
        new UpdateConnectorCommand(id, cmd.name(), cmd.type(), cmd.config()));
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<PageResult<ConnectorDTO>> page(ConnectorPageQuery qry) {
    PageResult<ConnectorDTO> result = connectorPageQueryHandler.handle(qry);
    return ApiResponse.success(result);
  }

  @GetMapping("/{id}")
  public ApiResponse<ConnectorDTO> detail(@PathVariable Long id) {
    ConnectorDTO dto = connectorDetailQueryHandler.handle(new ConnectorDetailQuery(id));
    return ApiResponse.success(dto);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteConnectorHandler.handle(new DeleteConnectorCommand(id));
    return ApiResponse.success();
  }

  @PostMapping("/{id}/test")
  public ApiResponse<Boolean> test(@PathVariable Long id) {
    Boolean success = testConnectorHandler.handle(new TestConnectorCommand(id));
    return ApiResponse.success(success);
  }

  @PostMapping("/{id}/enable")
  public ApiResponse<Void> enable(@PathVariable Long id) {
    enableConnectorHandler.handle(new EnableConnectorCommand(id));
    return ApiResponse.success();
  }

  @PostMapping("/{id}/disable")
  public ApiResponse<Void> disable(@PathVariable Long id) {
    disableConnectorHandler.handle(new DisableConnectorCommand(id));
    return ApiResponse.success();
  }
}
