package com.bone.system.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.system.adapter.web.converter.AlertWebConverter;
import com.bone.system.adapter.web.dto.req.AlertRulePageReq;
import com.bone.system.adapter.web.dto.req.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.req.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.resp.AlertRecordResp;
import com.bone.system.adapter.web.dto.resp.AlertRuleResp;
import com.bone.system.application.command.cmd.CreateAlertRuleCommand;
import com.bone.system.application.command.cmd.DisableAlertRuleCommand;
import com.bone.system.application.command.cmd.EnableAlertRuleCommand;
import com.bone.system.application.command.cmd.ResolveAlertCommand;
import com.bone.system.application.command.cmd.UpdateAlertRuleCommand;
import com.bone.system.application.command.handler.AlertCommandHandler;
import com.bone.system.application.query.dto.AlertRecordDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.handler.AlertQueryHandler;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AlertControllerTest {

  @Mock private AlertCommandHandler alertCommandHandler;

  @Mock private AlertQueryHandler alertQueryHandler;

  private AlertController alertController;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    AlertWebConverter alertWebConverter = Mappers.getMapper(AlertWebConverter.class);
    alertController =
        new AlertController(alertCommandHandler, alertQueryHandler, alertWebConverter);
  }

  @Test
  public void testCreateRule() {
    CreateAlertRuleReq req = new CreateAlertRuleReq();
    req.setName("testrule");
    req.setMetricName("cpu.usage");
    req.setThreshold(80.0);
    req.setAlertLevel("WARNING");

    Long ruleId = 1L;
    when(alertCommandHandler.handle(any(CreateAlertRuleCommand.class))).thenReturn(ruleId);

    ApiResponse<Long> apiResponse = alertController.createRule(req);

    assertTrue(apiResponse.isSuccess());
    assertEquals(ruleId, apiResponse.getData());
    verify(alertCommandHandler, times(1)).handle(any(CreateAlertRuleCommand.class));
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
    verify(alertCommandHandler, times(1)).handle(any(UpdateAlertRuleCommand.class));
  }

  @Test
  public void testEnableRule() {
    ApiResponse<Void> apiResponse = alertController.enableRule(1L);
    assertTrue(apiResponse.isSuccess());
    verify(alertCommandHandler, times(1)).handle(any(EnableAlertRuleCommand.class));
  }

  @Test
  public void testDisableRule() {
    ApiResponse<Void> apiResponse = alertController.disableRule(1L);
    assertTrue(apiResponse.isSuccess());
    verify(alertCommandHandler, times(1)).handle(any(DisableAlertRuleCommand.class));
  }

  @Test
  public void testDeleteRule() {
    ApiResponse<Void> apiResponse = alertController.deleteRule(1L);
    assertTrue(apiResponse.isSuccess());
    verify(alertCommandHandler, times(1)).delete(1L);
  }

  @Test
  public void testGetRuleById() {
    Long ruleId = 1L;
    AlertRuleDTO dto = AlertRuleDTO.builder().id(ruleId).name("test").build();
    when(alertQueryHandler.getRuleById(ruleId)).thenReturn(dto);

    ApiResponse<AlertRuleResp> apiResponse = alertController.getRuleById(ruleId);

    assertTrue(apiResponse.isSuccess());
    assertEquals(ruleId, apiResponse.getData().getId());
  }

  @Test
  public void testPageRules() {
    AlertRulePageReq req = new AlertRulePageReq();
    req.setPageNum(1);
    req.setPageSize(10);

    when(alertQueryHandler.pageRules(any()))
        .thenReturn(PageResult.of(Collections.emptyList(), 0L, 1, 10));

    ApiResponse<PageResult<AlertRuleResp>> apiResponse = alertController.pageRules(req);

    assertTrue(apiResponse.isSuccess());
    assertEquals(0, apiResponse.getData().getRecords().size());
  }

  @Test
  public void testCreateEvent() {
    when(alertCommandHandler.createAlertRecord(1L, 100.0)).thenReturn(1L);

    ApiResponse<Long> apiResponse = alertController.createEvent(1L, 100.0);

    assertTrue(apiResponse.isSuccess());
    assertEquals(1L, apiResponse.getData());
  }

  @Test
  public void testResolveEvent() {
    ApiResponse<Void> apiResponse = alertController.resolveEvent(1L);
    assertTrue(apiResponse.isSuccess());
    verify(alertCommandHandler, times(1)).handle(any(ResolveAlertCommand.class));
  }

  @Test
  public void testGetEventById() {
    Long eventId = 1L;
    AlertRecordDTO dto = AlertRecordDTO.builder().id(eventId).alertRuleId(1L).build();
    when(alertQueryHandler.getEventById(eventId)).thenReturn(dto);

    ApiResponse<AlertRecordResp> apiResponse = alertController.getEventById(eventId);

    assertTrue(apiResponse.isSuccess());
    assertEquals(eventId, apiResponse.getData().getId());
  }

  @Test
  public void testPageEvents() {
    when(alertQueryHandler.pageEvents(1, 10))
        .thenReturn(PageResult.of(Collections.emptyList(), 0L, 1, 10));

    com.bone.system.adapter.web.dto.req.AlertRecordPageReq req =
        new com.bone.system.adapter.web.dto.req.AlertRecordPageReq();
    req.setPageNum(1);
    req.setPageSize(10);
    ApiResponse<PageResult<AlertRecordResp>> apiResponse = alertController.pageEvents(req);

    assertTrue(apiResponse.isSuccess());
    assertEquals(0, apiResponse.getData().getRecords().size());
  }
}
