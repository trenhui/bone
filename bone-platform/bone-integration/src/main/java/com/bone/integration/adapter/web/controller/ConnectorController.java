package com.bone.integration.adapter.web.controller;

import com.bone.core.exception.DomainException;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.CreateConnectorCommand;
import com.bone.integration.application.command.cmd.UpdateConnectorCommand;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorPageQuery;
import com.bone.integration.application.query.handler.ConnectorPageQueryHandler;
import com.bone.integration.application.command.handler.CreateConnectorHandler;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.command.handler.UpdateConnectorHandler;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
import com.bone.integration.infrastructure.observability.IntegrationExecutionMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.INTEGRATION_V1 + "/connectors")
@RequiredArgsConstructor
public class ConnectorController {
    private final CreateConnectorHandler createConnectorHandler;
    private final UpdateConnectorHandler updateConnectorHandler;
    private final ConnectorPageQueryHandler connectorPageQueryHandler;
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;
    private final IntegrationDomainEventPublisher domainEventPublisher;
    private final IntegrationExecutionMetrics integrationMetrics;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateConnectorCommand cmd) {
        Long id = createConnectorHandler.handle(cmd);
        return ApiResponse.success(id);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateConnectorCommand cmd) {
        updateConnectorHandler.handle(new UpdateConnectorCommand(id, cmd.name(), cmd.type(), cmd.config()));
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<ConnectorDTO>> page(ConnectorPageQuery qry) {
        PageResult<ConnectorDTO> result = connectorPageQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ConnectorDTO> detail(@PathVariable Long id) {
        Connector connector = connectorRepository.findById(id);
        if (connector == null) {
            throw new DomainException("连接器不存在");
        }
        ConnectorDTO dto = new ConnectorDTO(
                connector.getId(),
                connector.getName(),
                connector.getType().name(),
                connector.getConfig(),
                connector.getStatus().name());
        return ApiResponse.success(dto);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        connectorRepository.deleteById(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Boolean> test(@PathVariable Long id) {
        Connector connector = connectorRepository.findById(id);
        if (connector == null) {
            throw new DomainException("连接器不存在");
        }
        boolean success = connectorService.testConnector(connector);
        integrationMetrics.recordConnectorTest(connector.getType().name(), success);
        String message = success ? "连接测试成功" : "连接测试失败";
        connector.recordTestResult(success, message);
        domainEventPublisher.publishFrom(connector);
        return ApiResponse.success(success);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        Connector connector = connectorRepository.findById(id);
        if (connector == null) {
            throw new DomainException("连接器不存在");
        }
        connector.enable();
        connectorRepository.save(connector);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        Connector connector = connectorRepository.findById(id);
        if (connector == null) {
            throw new DomainException("连接器不存在");
        }
        connector.disable();
        connectorRepository.save(connector);
        return ApiResponse.success();
    }
}
