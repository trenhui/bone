package com.bone.system.adapter.web.controller;

import com.bone.system.adapter.web.converter.LogWebConverter;
import com.bone.system.adapter.web.dto.req.CreateLogReq;
import com.bone.system.adapter.web.dto.req.LogPageReq;
import com.bone.system.adapter.web.dto.resp.LogResp;
import com.bone.system.application.command.cmd.CreateLogCommand;
import com.bone.system.application.command.handler.LogCommandHandler;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.handler.LogQueryHandler;
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
    private LogCommandHandler logCommandHandler;

    @Mock
    private LogQueryHandler logQueryHandler;

    private LogController logController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        LogWebConverter logWebConverter = Mappers.getMapper(LogWebConverter.class);
        logController = new LogController(logCommandHandler, logQueryHandler, logWebConverter);
    }

    @Test
    public void testCreate() {
        CreateLogReq req = new CreateLogReq();
        req.setContent("test log");
        req.setLogLevel("INFO");

        when(logCommandHandler.handle(any(CreateLogCommand.class))).thenReturn(1L);

        ApiResponse<Long> apiResponse = logController.create(req);

        assertTrue(apiResponse.isSuccess());
        assertEquals(1L, apiResponse.getData());
        verify(logCommandHandler, times(1)).handle(any(CreateLogCommand.class));
    }

    @Test
    public void testGetById() {
        Long logId = 1L;
        LogDTO dto = LogDTO.builder().id(logId).content("test").build();
        when(logQueryHandler.getById(logId)).thenReturn(dto);

        ApiResponse<LogResp> apiResponse = logController.getById(logId);

        assertTrue(apiResponse.isSuccess());
        assertEquals(logId, apiResponse.getData().getId());
    }

    @Test
    public void testPage() {
        LogPageReq req = new LogPageReq();
        req.setPageNum(1);
        req.setPageSize(10);

        when(logQueryHandler.page(any()))
                .thenReturn(PageResult.of(Collections.emptyList(), 0, 1, 10));

        ApiResponse<PageResult<LogResp>> apiResponse = logController.page(req);

        assertTrue(apiResponse.isSuccess());
        assertEquals(0, apiResponse.getData().getRecords().size());
    }
}
