package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.integration.application.ActivateFlowCommandApplicationService;
import com.bone.integration.application.CreateFlowApplicationService;
import com.bone.integration.application.DeactivateFlowCommandApplicationService;
import com.bone.integration.application.DeleteFlowCommandApplicationService;
import com.bone.integration.application.ExecuteFlowApplicationService;
import com.bone.integration.application.FlowDetailQueryApplicationService;
import com.bone.integration.application.FlowPageQueryApplicationService;
import com.bone.integration.application.FlowVersionListQueryApplicationService;
import com.bone.integration.application.UpdateFlowApplicationService;
import com.bone.integration.application.command.cmd.ActivateFlowCommand;
import com.bone.integration.application.command.cmd.CreateFlowCommand;
import com.bone.integration.application.command.cmd.DeactivateFlowCommand;
import com.bone.integration.application.command.cmd.DeleteFlowCommand;
import com.bone.integration.application.command.cmd.ExecuteFlowCommand;
import com.bone.integration.application.command.cmd.UpdateFlowCommand;
import com.bone.integration.application.query.dto.FlowDTO;
import com.bone.integration.application.query.dto.FlowDetailDTO;
import com.bone.integration.application.query.dto.FlowVersionDTO;
import com.bone.integration.application.query.qry.FlowDetailQuery;
import com.bone.integration.application.query.qry.FlowPageQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.INTEGRATION_V1 + "/flows")
@RequiredArgsConstructor
public class FlowController {
  private final CreateFlowApplicationService createFlowHandler;
  private final UpdateFlowApplicationService updateFlowHandler;
  private final FlowPageQueryApplicationService flowPageQueryHandler;
  private final FlowDetailQueryApplicationService flowDetailQueryHandler;
  private final ActivateFlowCommandApplicationService activateFlowCommandHandler;
  private final DeactivateFlowCommandApplicationService deactivateFlowCommandHandler;
  private final DeleteFlowCommandApplicationService deleteFlowCommandHandler;
  private final ExecuteFlowApplicationService executeFlowHandler;
  private final FlowVersionListQueryApplicationService flowVersionListQueryHandler;
  private final ObjectMapper objectMapper;

  @PostMapping
  public ApiResponse<Long> create(@RequestBody CreateFlowCommand cmd) {
    Long id = createFlowHandler.handle(cmd);
    return ApiResponse.success(id);
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateFlowCommand cmd) {
    updateFlowHandler.handle(
        new UpdateFlowCommand(id, cmd.name(), cmd.description(), cmd.nodes(), cmd.connections()));
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<PageResult<FlowDTO>> page(FlowPageQuery qry) {
    PageResult<FlowDTO> result = flowPageQueryHandler.handle(qry);
    return ApiResponse.success(result);
  }

  @GetMapping("/{id}")
  public ApiResponse<FlowDetailDTO> detail(@PathVariable Long id) {
    FlowDetailDTO dto = flowDetailQueryHandler.handle(new FlowDetailQuery(id));
    return ApiResponse.success(dto);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteFlowCommandHandler.handle(new DeleteFlowCommand(id));
    return ApiResponse.success();
  }

  @PostMapping("/{id}/activate")
  public ApiResponse<Void> activate(@PathVariable Long id) {
    activateFlowCommandHandler.handle(new ActivateFlowCommand(id));
    return ApiResponse.success();
  }

  @PostMapping("/{id}/deactivate")
  public ApiResponse<Void> deactivate(@PathVariable Long id) {
    deactivateFlowCommandHandler.handle(new DeactivateFlowCommand(id));
    return ApiResponse.success();
  }

  @PostMapping("/{id}/test")
  public ApiResponse<Long> test(
      @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
    String inputData = null;
    if (body != null && body.get("inputData") != null) {
      try {
        inputData = objectMapper.writeValueAsString(body.get("inputData"));
      } catch (Exception e) {
        inputData = String.valueOf(body.get("inputData"));
      }
    }
    Long executionId = executeFlowHandler.handle(new ExecuteFlowCommand(id, inputData));
    return ApiResponse.success(executionId);
  }

  @GetMapping("/{id}/versions")
  public ApiResponse<List<FlowVersionDTO>> versions(@PathVariable Long id) {
    return ApiResponse.success(flowVersionListQueryHandler.handle(id));
  }
}
