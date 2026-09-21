package com.bone.system.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.system.adapter.web.assembler.AlertAssembler;
import com.bone.system.adapter.web.dto.request.AlertRecordPageReq;
import com.bone.system.adapter.web.dto.request.AlertRulePageReq;
import com.bone.system.adapter.web.dto.request.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.request.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.response.AlertRecordResp;
import com.bone.system.adapter.web.dto.response.AlertRuleResp;
import com.bone.system.application.AlertApplicationService;
import com.bone.system.application.command.CreateAlertRuleCommand;
import com.bone.system.application.command.UpdateAlertRuleCommand;
import com.bone.system.application.query.dto.AlertRecordDto;
import com.bone.system.application.query.dto.AlertRuleDto;
import com.bone.system.application.query.qry.AlertRecordPageQuery;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** 告警控制器切片测试：Controller 只做协议转换与响应包装，用例行为由应用层测试覆盖。 */
public class AlertControllerTest {

  @Mock private AlertApplicationService alertApplicationService;

  private AlertController alertController;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    AlertAssembler alertAssembler = Mappers.getMapper(AlertAssembler.class);
    alertController = new AlertController(alertApplicationService, alertAssembler);
  }

  @Test
  public void testCreateRule() {
    CreateAlertRuleReq req = new CreateAlertRuleReq();
    req.setName("testrule");
    req.setMetricName("cpu.usage");
    req.setThreshold(80.0);
    req.setAlertLevel("WARNING");

    Long ruleId = 1L;
    when(alertApplicationService.createRule(any(CreateAlertRuleCommand.class))).thenReturn(ruleId);

    ApiResponse<Long> apiResponse = alertController.createRule(req);

    assertTrue(apiResponse.isSuccess());
    assertEquals(ruleId, apiResponse.getData());
    verify(alertApplicationService, times(1)).createRule(any(CreateAlertRuleCommand.class));
  }

  @Test
  public void testUpdateRule() {
    UpdateAlertRuleReq req = new UpdateAlertRuleReq();
    req.setId(1L);
    req.setName("updated");
    req.setThreshold(90.0);
    req.setAlertLevel("CRITICAL");

    ApiResponse<Void> apiResponse = alertController.updateRule(req);

    assertTrue(apiResponse.isSuccess());
    verify(alertApplicationService, times(1)).updateRule(any(UpdateAlertRuleCommand.class));
  }

  @Test
  public void testEnableRule() {
    ApiResponse<Void> apiResponse = alertController.enableRule(1L);
    assertTrue(apiResponse.isSuccess());
    verify(alertApplicationService, times(1)).enableRule(1L);
  }

  @Test
  public void testDisableRule() {
    ApiResponse<Void> apiResponse = alertController.disableRule(1L);
    assertTrue(apiResponse.isSuccess());
    verify(alertApplicationService, times(1)).disableRule(1L);
  }

  @Test
  public void testDeleteRule() {
    ApiResponse<Void> apiResponse = alertController.deleteRule(1L);
    assertTrue(apiResponse.isSuccess());
    verify(alertApplicationService, times(1)).deleteRule(1L);
  }

  @Test
  public void testGetRuleById() {
    Long ruleId = 1L;
    AlertRuleDto dto = AlertRuleDto.builder().id(ruleId).name("test").build();
    when(alertApplicationService.getRuleById(ruleId)).thenReturn(Optional.of(dto));

    ApiResponse<AlertRuleResp> apiResponse = alertController.getRuleById(ruleId);

    assertTrue(apiResponse.isSuccess());
    assertEquals(ruleId, apiResponse.getData().getId());
  }

  @Test
  public void testPageRules() {
    AlertRulePageReq req = new AlertRulePageReq();
    req.setPageNum(1);
    req.setPageSize(10);

    when(alertApplicationService.pageRules(any()))
        .thenReturn(PageResult.of(Collections.emptyList(), 0L, 1, 10));

    ApiResponse<PageResult<AlertRuleResp>> apiResponse = alertController.pageRules(req);

    assertTrue(apiResponse.isSuccess());
    assertEquals(0, apiResponse.getData().getRecords().size());
  }

  @Test
  public void testRecordIfTriggered() {
    when(alertApplicationService.recordIfTriggered(anyLong(), anyDouble()))
        .thenReturn(Optional.of(1L));

    ApiResponse<Long> apiResponse = alertController.recordIfTriggered(1L, 100.0);

    assertTrue(apiResponse.isSuccess());
    assertEquals(1L, apiResponse.getData());
  }

  /** 未达阈值返回空 data：「上报了但没告警」是正常分支，不是失败。 */
  @Test
  public void testRecordNotTriggeredReturnsNullBody() {
    when(alertApplicationService.recordIfTriggered(anyLong(), anyDouble()))
        .thenReturn(Optional.empty());

    ApiResponse<Long> apiResponse = alertController.recordIfTriggered(1L, 1.0);

    assertTrue(apiResponse.isSuccess());
    assertNull(apiResponse.getData());
  }

  @Test
  public void testResolveEvent() {
    ApiResponse<Void> apiResponse = alertController.resolveEvent(1L);
    assertTrue(apiResponse.isSuccess());
    verify(alertApplicationService, times(1)).resolveRecord(1L);
  }

  @Test
  public void testGetEventById() {
    Long eventId = 1L;
    AlertRecordDto dto = AlertRecordDto.builder().id(eventId).alertRuleId(1L).build();
    when(alertApplicationService.getRecordById(eventId)).thenReturn(Optional.of(dto));

    ApiResponse<AlertRecordResp> apiResponse = alertController.getEventById(eventId);

    assertTrue(apiResponse.isSuccess());
    assertEquals(eventId, apiResponse.getData().getId());
  }

  @Test
  public void testPageEvents() {
    when(alertApplicationService.pageRecords(any(AlertRecordPageQuery.class)))
        .thenReturn(PageResult.of(Collections.emptyList(), 0L, 1, 10));

    AlertRecordPageReq req = new AlertRecordPageReq();
    req.setPageNum(1);
    req.setPageSize(10);
    ApiResponse<PageResult<AlertRecordResp>> apiResponse = alertController.pageEvents(req);

    assertTrue(apiResponse.isSuccess());
    assertEquals(0, apiResponse.getData().getRecords().size());
  }
}
