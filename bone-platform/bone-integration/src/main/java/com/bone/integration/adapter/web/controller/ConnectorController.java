package com.bone.integration.adapter.web.controller;

import com.bone.core.exception.DomainException;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.CreateConnectorCmd;
import com.bone.integration.application.command.cmd.UpdateConnectorCmd;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorPageQry;
import com.bone.integration.application.usecase.standard.ConnectorPageQueryUseCase;
import com.bone.integration.application.usecase.standard.CreateConnectorUseCase;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.usecase.standard.UpdateConnectorUseCase;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.INTEGRATION_V1 + "/connectors")
@RequiredArgsConstructor
public class ConnectorController {
    private final CreateConnectorUseCase createConnectorUseCase;
    private final UpdateConnectorUseCase updateConnectorUseCase;
    private final ConnectorPageQueryUseCase connectorPageQueryUseCase;
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;
    private final IntegrationDomainEventPublisher domainEventPublisher;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateConnectorCmd cmd) {
        Long id = createConnectorUseCase.execute(cmd);
        return ApiResponse.success(id);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateConnectorCmd cmd) {
        updateConnectorUseCase.execute(new UpdateConnectorCmd(id, cmd.name(), cmd.type(), cmd.config()));
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<ConnectorDTO>> page(ConnectorPageQry qry) {
        PageResult<ConnectorDTO> result = connectorPageQueryUseCase.execute(qry);
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
