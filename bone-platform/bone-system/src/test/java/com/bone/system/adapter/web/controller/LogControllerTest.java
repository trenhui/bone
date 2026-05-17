package com.bone.system.adapter.web.controller;

import com.bone.system.adapter.web.converter.LogWebConverter;
import com.bone.system.adapter.web.dto.req.CreateLogReq;
import com.bone.system.adapter.web.dto.req.LogPageReq;
import com.bone.system.adapter.web.dto.resp.LogResp;
import com.bone.system.application.command.cmd.CreateLogCmd;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.usecase.standard.CreateLogUseCase;
import com.bone.system.application.usecase.standard.LogByIdQueryUseCase;
import com.bone.system.application.usecase.standard.LogPageQueryUseCase;
import com.bone.system.common.result.ApiResponse;
import com.bone.system.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogControllerTest {

    @Mock
    private CreateLogUseCase createLogUseCase;

    @Mock
    private LogByIdQueryUseCase logByIdQueryUseCase;

    @Mock
    private LogPageQueryUseCase logPageQueryUseCase;

    private LogController logController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        LogWebConverter logWebConverter = Mappers.getMapper(LogWebConverter.class);
        logController = new LogController(createLogUseCase, logByIdQueryUseCase, logPageQueryUseCase, logWebConverter);
    }

    @Test
    public void testCreate() {
        CreateLogReq req = new CreateLogReq();
        req.setLogLevel("INFO");
        req.setServiceName("bone-system");
        req.setContent("Test log");

        Long logId = 1L;
        when(createLogUseCase.execute(any(CreateLogCmd.class))).thenReturn(logId);

        ApiResponse<Long> apiResponse = logController.create(req);

        assertTrue(apiResponse.isSuccess());
        assertEquals(logId, apiResponse.getData());
        verify(createLogUseCase, times(1)).execute(any(CreateLogCmd.class));
    }

    @Test
    public void testGetById() {
        Long logId = 1L;
        LogDTO dto = new LogDTO();
        dto.setId(logId);
        dto.setLogLevel("INFO");
        dto.setServiceName("bone-system");
        dto.setContent("Test log");

        when(logByIdQueryUseCase.execute(logId)).thenReturn(dto);

        ApiResponse<LogResp> apiResponse = logController.getById(logId);

        assertTrue(apiResponse.isSuccess());
        assertEquals(logId, apiResponse.getData().getId());
    }

    @Test
    public void testPage() {
        LogPageReq req = new LogPageReq();
        req.setPageNum(1);
        req.setPageSize(10);

        when(logPageQueryUseCase.execute(any()))
                .thenReturn(PageResult.of(Collections.emptyList(), 0, 1, 10));

        ApiResponse<PageResult<LogResp>> apiResponse = logController.page(req);

        assertTrue(apiResponse.isSuccess());
        assertEquals(0, apiResponse.getData().getRecords().size());
    }
}
