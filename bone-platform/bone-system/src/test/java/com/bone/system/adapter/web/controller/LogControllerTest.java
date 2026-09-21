package com.bone.system.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.system.adapter.web.assembler.LogAssembler;
import com.bone.system.adapter.web.dto.request.CreateLogReq;
import com.bone.system.adapter.web.dto.request.LogPageReq;
import com.bone.system.adapter.web.dto.response.LogResp;
import com.bone.system.application.LogExportApplicationService;
import com.bone.system.application.SystemLogApplicationService;
import com.bone.system.application.command.CreateLogCommand;
import com.bone.system.application.query.dto.LogDto;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** 日志控制器切片测试：Controller 只做协议转换与响应包装，用例行为由应用层测试覆盖。 */
public class LogControllerTest {

  @Mock private SystemLogApplicationService systemLogApplicationService;

  @Mock private LogExportApplicationService logExportApplicationService;

  private LogController logController;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    LogAssembler logAssembler = Mappers.getMapper(LogAssembler.class);
    logController =
        new LogController(systemLogApplicationService, logExportApplicationService, logAssembler);
  }

  @Test
  public void testCreate() {
    CreateLogReq req = new CreateLogReq();
    req.setContent("test log");
    req.setLogLevel("INFO");

    when(systemLogApplicationService.create(any(CreateLogCommand.class))).thenReturn(1L);

    ApiResponse<Long> apiResponse = logController.create(req);

    assertTrue(apiResponse.isSuccess());
    assertEquals(1L, apiResponse.getData());
    verify(systemLogApplicationService, times(1)).create(any(CreateLogCommand.class));
  }

  @Test
  public void testGetById() {
    Long logId = 1L;
    LogDto dto = LogDto.builder().id(logId).content("test").build();
    when(systemLogApplicationService.getById(logId)).thenReturn(Optional.of(dto));

    ApiResponse<LogResp> apiResponse = logController.getById(logId);

    assertTrue(apiResponse.isSuccess());
    assertEquals(logId, apiResponse.getData().getId());
  }

  @Test
  public void testGetByIdNotFoundReturnsNullBody() {
    when(systemLogApplicationService.getById(404L)).thenReturn(Optional.empty());

    ApiResponse<LogResp> apiResponse = logController.getById(404L);

    assertTrue(apiResponse.isSuccess());
    assertEquals(null, apiResponse.getData());
  }

  @Test
  public void testPage() {
    LogPageReq req = new LogPageReq();
    req.setPageNum(1);
    req.setPageSize(10);

    when(systemLogApplicationService.page(any()))
        .thenReturn(PageResult.of(Collections.emptyList(), 0L, 1, 10));

    ApiResponse<PageResult<LogResp>> apiResponse = logController.page(req);

    assertTrue(apiResponse.isSuccess());
    assertEquals(0, apiResponse.getData().getRecords().size());
  }
}
