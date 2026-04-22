package com.bone.integration.adapter.web.controller;

import com.bone.integration.application.command.cmd.CreateConnectorCmd;
import com.bone.integration.application.command.cmd.UpdateConnectorCmd;
import com.bone.integration.application.command.handler.CreateConnectorHandler;
import com.bone.integration.application.command.handler.UpdateConnectorHandler;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.handler.ConnectorPageQueryHandler;
import com.bone.integration.application.query.qry.ConnectorPageQry;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
import com.bone.core.model.PageResult;
import com.bone.core.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/integration/connectors")
@RequiredArgsConstructor
public class ConnectorController {
    private final CreateConnectorHandler createConnectorHandler;
    private final UpdateConnectorHandler updateConnectorHandler;
    private final ConnectorPageQueryHandler connectorPageQueryHandler;
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateConnectorCmd cmd) {
        Long id = createConnectorHandler.handle(cmd);
        return ApiResponse.success(id);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateConnectorCmd cmd) {
        updateConnectorHandler.handle(new UpdateConnectorCmd(id, cmd.name(), cmd.type(), cmd.config()));
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<ConnectorDTO>> page(ConnectorPageQry qry) {
        PageResult<ConnectorDTO> result = connectorPageQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ConnectorDTO> detail(@PathVariable Long id) {
        Connector connector = connectorRepository.findById(id)
                .orElseThrow(() -> new com.bone.core.exception.DomainException("连接器不存在"));
        ConnectorDTO dto = new ConnectorDTO(
                connector.getId().value(),
                connector.getName(),
                connector.getType().name(),
                connector.getConfig(),
                connector.getStatus().name()
        );
        return ApiResponse.success(dto);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        connectorRepository.deleteById(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Boolean> test(@PathVariable Long id) {
        Connector connector = connectorRepository.findById(id)
                .orElseThrow(() -> new com.bone.core.exception.DomainException("连接器不存在"));
        boolean success = connectorService.testConnector(connector);
        return ApiResponse.success(success);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        Connector connector = connectorRepository.findById(id)
                .orElseThrow(() -> new com.bone.core.exception.DomainException("连接器不存在"));
        connector.enable();
        connectorRepository.save(connector);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        Connector connector = connectorRepository.findById(id)
                .orElseThrow(() -> new com.bone.core.exception.DomainException("连接器不存在"));
        connector.disable();
        connectorRepository.save(connector);
        return ApiResponse.success();
    }
}