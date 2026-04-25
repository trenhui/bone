package com.bone.system.adapter.web.controller;

import com.bone.system.adapter.web.converter.LogWebConverter;
import com.bone.system.adapter.web.dto.req.CreateLogReq;
import com.bone.system.adapter.web.dto.req.LogPageReq;
import com.bone.system.adapter.web.dto.resp.LogResp;
import com.bone.system.application.command.cmd.CreateLogCmd;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.qry.LogPageQry;
import com.bone.system.application.usecase.standard.CreateLogUseCase;
import com.bone.system.application.usecase.standard.LogByIdQueryUseCase;
import com.bone.system.application.usecase.standard.LogPageQueryUseCase;
import com.bone.system.common.result.ApiResponse;
import com.bone.system.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class LogControllerTest {

    @Mock
    private CreateLogUseCase createLogUseCase;

    @Mock
    private LogByIdQueryUseCase logByIdQueryUseCase;

    @Mock
    private LogPageQueryUseCase logPageQueryUseCase;

    @Mock
    private LogWebConverter logWebConverter;

    @InjectMocks
    private LogController logController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCreate() {
        // 准备测试数据
        CreateLogReq req = new CreateLogReq();
        req.setLogType("INFO");
        req.setContent("Test log");

        CreateLogCmd cmd = new CreateLogCmd();
        cmd.setLogType("INFO");
        cmd.setContent("Test log");

        Long logId = 1L;

        // 模拟依赖
        when(logWebConverter.toCmd(req)).thenReturn(cmd);
        when(createLogUseCase.execute(cmd)).thenReturn(logId);

        // 执行测试
        ApiResponse<Long> apiResponse = logController.create(req);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(logId, apiResponse.getData());
        verify(logWebConverter, times(1)).toCmd(req);
        verify(createLogUseCase, times(1)).execute(cmd);
    }

    @Test
    public void testGetById() {
        // 准备测试数据
        Long logId = 1L;

        LogDTO dto = new LogDTO();
        dto.setId(logId);
        dto.setLogType("INFO");
        dto.setContent("Test log");

        LogResp resp = new LogResp();
        resp.setId(logId);
        resp.setLogType("INFO");
        resp.setContent("Test log");

        // 模拟依赖
        when(logByIdQueryUseCase.execute(logId)).thenReturn(dto);
        when(logWebConverter.toResp(dto)).thenReturn(resp);

        // 执行测试
        ApiResponse<LogResp> apiResponse = logController.getById(logId);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(resp, apiResponse.getData());
        verify(logByIdQueryUseCase, times(1)).execute(logId);
        verify(logWebConverter, times(1)).toResp(dto);
    }

    @Test
    public void testPage() {
        // 准备测试数据
        LogPageReq req = new LogPageReq();
        req.setPageNum(1);
        req.setPageSize(10);

        LogPageQry qry = new LogPageQry();
        qry.setPageNum(1);
        qry.setPageSize(10);

        PageResult<LogDTO> pageResult = new PageResult<>();
        pageResult.setList(Collections.emptyList());
        pageResult.setTotal(0);
        pageResult.setPageNum(1);
        pageResult.setPageSize(10);

        PageResult<LogResp> respPageResult = new PageResult<>();
        respPageResult.setList(Collections.emptyList());
        respPageResult.setTotal(0);
        respPageResult.setPageNum(1);
        respPageResult.setPageSize(10);

        // 模拟依赖
        when(logWebConverter.toQry(req)).thenReturn(qry);
        when(logPageQueryUseCase.execute(qry)).thenReturn(pageResult);
        when(pageResult.map(logWebConverter::toResp)).thenReturn(respPageResult);

        // 执行测试
        ApiResponse<PageResult<LogResp>> apiResponse = logController.page(req);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(respPageResult, apiResponse.getData());
        verify(logWebConverter, times(1)).toQry(req);
        verify(logPageQueryUseCase, times(1)).execute(qry);
        verify(pageResult, times(1)).map(logWebConverter::toResp);
    }
}
