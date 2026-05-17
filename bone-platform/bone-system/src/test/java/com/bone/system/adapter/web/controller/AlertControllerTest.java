package com.bone.system.adapter.web.controller;

import com.bone.system.adapter.web.converter.AlertWebConverter;
import com.bone.system.adapter.web.dto.req.AlertRulePageReq;
import com.bone.system.adapter.web.dto.req.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.req.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.resp.AlertEventResp;
import com.bone.system.adapter.web.dto.resp.AlertRuleResp;
import com.bone.system.application.command.cmd.CreateAlertRuleCmd;
import com.bone.system.application.command.cmd.DisableAlertRuleCmd;
import com.bone.system.application.command.cmd.EnableAlertRuleCmd;
import com.bone.system.application.command.cmd.ResolveAlertCmd;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.usecase.standard.AlertEventByIdQueryUseCase;
import com.bone.system.application.usecase.standard.AlertEventPageQueryUseCase;
import com.bone.system.application.usecase.standard.AlertRuleByIdQueryUseCase;
import com.bone.system.application.usecase.standard.AlertRulePageQueryUseCase;
import com.bone.system.application.usecase.standard.CreateAlertEventUseCase;
import com.bone.system.application.usecase.standard.CreateAlertRuleUseCase;
import com.bone.system.application.usecase.standard.DeleteAlertRuleUseCase;
import com.bone.system.application.usecase.standard.DisableAlertRuleUseCase;
import com.bone.system.application.usecase.standard.EnableAlertRuleUseCase;
import com.bone.system.application.usecase.standard.ResolveAlertUseCase;
import com.bone.system.application.usecase.standard.UpdateAlertRuleUseCase;
import com.bone.system.common.result.ApiResponse;
import com.bone.system.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AlertControllerTest {

    @Mock
    private CreateAlertRuleUseCase createAlertRuleUseCase;

    @Mock
    private UpdateAlertRuleUseCase updateAlertRuleUseCase;

    @Mock
    private EnableAlertRuleUseCase enableAlertRuleUseCase;

    @Mock
    private DisableAlertRuleUseCase disableAlertRuleUseCase;

    @Mock
    private DeleteAlertRuleUseCase deleteAlertRuleUseCase;

    @Mock
    private CreateAlertEventUseCase createAlertEventUseCase;

    @Mock
    private ResolveAlertUseCase resolveAlertUseCase;

    @Mock
    private AlertRuleByIdQueryUseCase alertRuleByIdQueryUseCase;

    @Mock
    private AlertRulePageQueryUseCase alertRulePageQueryUseCase;

    @Mock
    private AlertEventByIdQueryUseCase alertEventByIdQueryUseCase;

    @Mock
    private AlertEventPageQueryUseCase alertEventPageQueryUseCase;

    private AlertController alertController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        AlertWebConverter alertWebConverter = Mappers.getMapper(AlertWebConverter.class);
        alertController = new AlertController(
                createAlertRuleUseCase,
                updateAlertRuleUseCase,
                enableAlertRuleUseCase,
                disableAlertRuleUseCase,
                deleteAlertRuleUseCase,
                createAlertEventUseCase,
                resolveAlertUseCase,
                alertRuleByIdQueryUseCase,
                alertRulePageQueryUseCase,
                alertEventByIdQueryUseCase,
                alertEventPageQueryUseCase,
                alertWebConverter);
    }

    @Test
    public void testCreateRule() {
        CreateAlertRuleReq req = new CreateAlertRuleReq();
        req.setName("testrule");
        req.setMetricName("cpu.usage");
        req.setThreshold(80.0);
        req.setAlertLevel("WARNING");

        Long ruleId = 1L;
        when(createAlertRuleUseCase.execute(any(CreateAlertRuleCmd.class))).thenReturn(ruleId);

        ApiResponse<Long> apiResponse = alertController.createRule(req);

        assertTrue(apiResponse.isSuccess());
        assertEquals(ruleId, apiResponse.getData());
        verify(createAlertRuleUseCase, times(1)).execute(any(CreateAlertRuleCmd.class));
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
        verify(updateAlertRuleUseCase, times(1)).execute(any());
    }

    @Test
    public void testEnableRule() {
        ApiResponse<Void> apiResponse = alertController.enableRule(1L);
        assertTrue(apiResponse.isSuccess());
        verify(enableAlertRuleUseCase, times(1)).execute(any(EnableAlertRuleCmd.class));
    }

    @Test
    public void testDisableRule() {
        ApiResponse<Void> apiResponse = alertController.disableRule(1L);
        assertTrue(apiResponse.isSuccess());
        verify(disableAlertRuleUseCase, times(1)).execute(any(DisableAlertRuleCmd.class));
    }

    @Test
    public void testDeleteRule() {
        ApiResponse<Void> apiResponse = alertController.deleteRule(1L);
        assertTrue(apiResponse.isSuccess());
        verify(deleteAlertRuleUseCase, times(1)).execute(1L);
    }

    @Test
    public void testGetRuleById() {
        Long ruleId = 1L;
        AlertRuleDTO dto = AlertRuleDTO.builder().id(ruleId).name("test").build();
        when(alertRuleByIdQueryUseCase.execute(ruleId)).thenReturn(dto);

        ApiResponse<AlertRuleResp> apiResponse = alertController.getRuleById(ruleId);

        assertTrue(apiResponse.isSuccess());
        assertEquals(ruleId, apiResponse.getData().getId());
    }

    @Test
    public void testPageRules() {
        AlertRulePageReq req = new AlertRulePageReq();
        req.setPageNum(1);
        req.setPageSize(10);

        when(alertRulePageQueryUseCase.execute(any()))
                .thenReturn(PageResult.of(Collections.emptyList(), 0, 1, 10));

        ApiResponse<PageResult<AlertRuleResp>> apiResponse = alertController.pageRules(req);

        assertTrue(apiResponse.isSuccess());
        assertEquals(0, apiResponse.getData().getRecords().size());
    }

    @Test
    public void testCreateEvent() {
        when(createAlertEventUseCase.execute(any())).thenReturn(1L);

        ApiResponse<Long> apiResponse = alertController.createEvent(1L, 100.0);

        assertTrue(apiResponse.isSuccess());
        assertEquals(1L, apiResponse.getData());
    }

    @Test
    public void testResolveEvent() {
        ApiResponse<Void> apiResponse = alertController.resolveEvent(1L);
        assertTrue(apiResponse.isSuccess());
        verify(resolveAlertUseCase, times(1)).execute(any(ResolveAlertCmd.class));
    }

    @Test
    public void testGetEventById() {
        Long eventId = 1L;
        AlertEventDTO dto = AlertEventDTO.builder().id(eventId).alertRuleId(1L).build();
        when(alertEventByIdQueryUseCase.execute(eventId)).thenReturn(dto);

        ApiResponse<AlertEventResp> apiResponse = alertController.getEventById(eventId);

        assertTrue(apiResponse.isSuccess());
        assertEquals(eventId, apiResponse.getData().getId());
    }

    @Test
    public void testPageEvents() {
        when(alertEventPageQueryUseCase.execute(any(int[].class)))
                .thenReturn(PageResult.of(Collections.emptyList(), 0, 1, 10));

        com.bone.system.adapter.web.dto.req.AlertEventPageReq req =
                new com.bone.system.adapter.web.dto.req.AlertEventPageReq();
        req.setPageNum(1);
        req.setPageSize(10);
        ApiResponse<PageResult<AlertEventResp>> apiResponse = alertController.pageEvents(req);

        assertTrue(apiResponse.isSuccess());
        assertEquals(0, apiResponse.getData().getRecords().size());
    }
}
