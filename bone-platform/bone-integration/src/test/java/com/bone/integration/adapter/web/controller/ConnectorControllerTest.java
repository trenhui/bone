package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.CreateConnectorCmd;
import com.bone.integration.application.command.cmd.UpdateConnectorCmd;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorPageQry;
import com.bone.integration.application.usecase.standard.CreateConnectorUseCase;
import com.bone.integration.application.usecase.standard.UpdateConnectorUseCase;
import com.bone.integration.application.usecase.standard.ConnectorPageQueryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ConnectorControllerTest {

    @Mock
    private CreateConnectorUseCase createConnectorUseCase;

    @Mock
    private UpdateConnectorUseCase updateConnectorUseCase;

    @Mock
    private ConnectorPageQueryUseCase connectorPageQueryUseCase;

    @InjectMocks
    private ConnectorController connectorController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCreate() {
        // 准备测试数据
        CreateConnectorCmd cmd = new CreateConnectorCmd();
        cmd.setName("testconnector");
        cmd.setType("HTTP");

        Long connectorId = 1L;

        // 模拟依赖
        when(createConnectorUseCase.execute(cmd)).thenReturn(connectorId);

        // 执行测试
        ApiResponse<Long> apiResponse = connectorController.create(cmd);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(connectorId, apiResponse.getData());
        verify(createConnectorUseCase, times(1)).execute(cmd);
    }

    @Test
    public void testUpdate() {
        // 准备测试数据
        Long connectorId = 1L;
        UpdateConnectorCmd cmd = new UpdateConnectorCmd();
        cmd.setName("updatedconnector");
        cmd.setType("HTTP");

        UpdateConnectorCmd updatedCmd = new UpdateConnectorCmd(connectorId, cmd.name(), cmd.type(), cmd.config());

        // 执行测试
        ApiResponse<Void> apiResponse = connectorController.update(connectorId, cmd);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        verify(updateConnectorUseCase, times(1)).execute(updatedCmd);
    }

    @Test
    public void testPage() {
        // 准备测试数据
        ConnectorPageQry qry = new ConnectorPageQry();
        qry.setPageNum(1);
        qry.setPageSize(10);

        PageResult<ConnectorDTO> pageResult = new PageResult<>();
        pageResult.setList(Collections.emptyList());
        pageResult.setTotal(0);
        pageResult.setPageNum(1);
        pageResult.setPageSize(10);

        // 模拟依赖
        when(connectorPageQueryUseCase.execute(qry)).thenReturn(pageResult);

        // 执行测试
        ApiResponse<PageResult<ConnectorDTO>> apiResponse = connectorController.page(qry);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(pageResult, apiResponse.getData());
        verify(connectorPageQueryUseCase, times(1)).execute(qry);
    }
}
