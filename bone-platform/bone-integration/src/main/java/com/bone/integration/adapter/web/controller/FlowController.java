package com.bone.integration.adapter.web.controller;

import com.bone.core.exception.DomainException;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.CreateFlowCmd;
import com.bone.integration.application.command.cmd.UpdateFlowCmd;
import com.bone.integration.application.query.dto.FlowDTO;
import com.bone.integration.application.query.qry.FlowPageQry;
import com.bone.integration.application.usecase.standard.CreateFlowUseCase;
import com.bone.integration.application.usecase.standard.FlowPageQueryUseCase;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.usecase.standard.UpdateFlowUseCase;
import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.service.FlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(PlatformApiPaths.INTEGRATION_V1 + "/flows")
@RequiredArgsConstructor
public class FlowController {
    private final CreateFlowUseCase createFlowUseCase;
    private final UpdateFlowUseCase updateFlowUseCase;
    private final FlowPageQueryUseCase flowPageQueryUseCase;
    private final IntegrationFlowRepository flowRepository;
    private final FlowService flowService;
    private final IntegrationDomainEventPublisher domainEventPublisher;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateFlowCmd cmd) {
        Long id = createFlowUseCase.execute(cmd);
        return ApiResponse.success(id);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateFlowCmd cmd) {
        updateFlowUseCase.execute(new UpdateFlowCmd(id, cmd.name(), cmd.description(), cmd.nodes(), cmd.connections()));
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<FlowDTO>> page(FlowPageQry qry) {
        PageResult<FlowDTO> result = flowPageQueryUseCase.execute(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<FlowDTO> detail(@PathVariable Long id) {
        IntegrationFlow flow = flowRepository.findById(id);
        if (flow == null) {
            throw new DomainException("流程不存在");
        }
        List<FlowNode> nodes = flowService.getFlowNodes(flow.getId());
        List<FlowConnection> connections = flowService.getFlowConnections(flow.getId());

        List<FlowDTO.FlowNodeDTO> nodeDTOs = nodes.stream()
                .map(node -> new FlowDTO.FlowNodeDTO(
                        node.getId(),
                        node.getName(),
                        node.getType().name(),
                        node.getConfig(),
                        node.getPositionX(),
                        node.getPositionY()))
                .collect(Collectors.toList());

        List<FlowDTO.FlowConnectionDTO> connectionDTOs = connections.stream()
                .map(conn -> new FlowDTO.FlowConnectionDTO(
                        conn.getId(), conn.getSourceNodeId(), conn.getTargetNodeId(), conn.getCondition()))
                .collect(Collectors.toList());

        FlowDTO dto = new FlowDTO(
                flow.getId(),
                flow.getName(),
                flow.getDescription(),
                flow.getStatus().name(),
                nodeDTOs,
                connectionDTOs);
        return ApiResponse.success(dto);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        flowRepository.deleteById(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable Long id) {
        IntegrationFlow flow = flowRepository.findById(id);
        if (flow == null) {
            throw new DomainException("流程不存在");
        }
        flow.activate();
        flowRepository.save(flow);
        domainEventPublisher.publishFrom(flow);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        IntegrationFlow flow = flowRepository.findById(id);
        if (flow == null) {
            throw new DomainException("流程不存在");
        }
        flow.deactivate();
        flowRepository.save(flow);
        return ApiResponse.success();
    }
}
