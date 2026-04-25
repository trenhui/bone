package com.bone.system.adapter.web.controller;

import com.bone.system.adapter.web.converter.AlertWebConverter;
import com.bone.system.adapter.web.dto.req.AlertEventPageReq;
import com.bone.system.adapter.web.dto.req.AlertRulePageReq;
import com.bone.system.adapter.web.dto.req.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.req.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.resp.AlertEventResp;
import com.bone.system.adapter.web.dto.resp.AlertRuleResp;
import com.bone.system.application.command.cmd.DisableAlertRuleCmd;
import com.bone.system.application.command.cmd.EnableAlertRuleCmd;
import com.bone.system.application.command.cmd.ResolveAlertCmd;
import com.bone.system.application.command.cmd.CreateAlertRuleCmd;
import com.bone.system.application.command.cmd.UpdateAlertRuleCmd;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.qry.AlertRulePageQry;
import com.bone.system.application.usecase.standard.CreateAlertRuleUseCase;
import com.bone.system.application.usecase.standard.UpdateAlertRuleUseCase;
import com.bone.system.application.usecase.standard.EnableAlertRuleUseCase;
import com.bone.system.application.usecase.standard.DisableAlertRuleUseCase;
import com.bone.system.application.usecase.standard.DeleteAlertRuleUseCase;
import com.bone.system.application.usecase.standard.CreateAlertEventUseCase;
import com.bone.system.application.usecase.standard.ResolveAlertUseCase;
import com.bone.system.application.usecase.standard.AlertRuleByIdQueryUseCase;
import com.bone.system.application.usecase.standard.AlertRulePageQueryUseCase;
import com.bone.system.application.usecase.standard.AlertEventByIdQueryUseCase;
import com.bone.system.application.usecase.standard.AlertEventPageQueryUseCase;
import com.bone.system.common.result.ApiResponse;
import com.bone.system.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

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

    @Mock
    private AlertWebConverter alertWebConverter;

    @InjectMocks
    private AlertController alertController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCreateRule() {
        // 准备测试数据
        CreateAlertRuleReq req = new CreateAlertRuleReq();
        req.setName("testrule");
        req.setRuleType("THRESHOLD");

        CreateAlertRuleCmd cmd = new CreateAlertRuleCmd();
        cmd.setName("testrule");
        cmd.setRuleType("THRESHOLD");

        Long ruleId = 1L;

        // 模拟依赖
        when(alertWebConverter.toCmd(req)).thenReturn(cmd);
        when(createAlertRuleUseCase.execute(cmd)).thenReturn(ruleId);

        // 执行测试
        ApiResponse<Long> apiResponse = alertController.createRule(req);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(ruleId, apiResponse.getData());
        verify(alertWebConverter, times(1)).toCmd(req);
        verify(createAlertRuleUseCase, times(1)).execute(cmd);
    }

    @Test
    public void testUpdateRule() {
        // 准备测试数据
        UpdateAlertRuleReq req = new UpdateAlertRuleReq();
        req.setId(1L);
        req.setName("updatedrule");

        UpdateAlertRuleCmd cmd = new UpdateAlertRuleCmd();
        cmd.setId(1L);
        cmd.setName("updatedrule");

        // 模拟依赖
        when(alertWebConverter.toCmd(req)).thenReturn(cmd);

        // 执行测试
        ApiResponse<Void> apiResponse = alertController.updateRule(req);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        verify(alertWebConverter, times(1)).toCmd(req);
        verify(updateAlertRuleUseCase, times(1)).execute(cmd);
    }

    @Test
    public void testEnableRule() {
        // 准备测试数据
        Long ruleId = 1L;

        EnableAlertRuleCmd cmd = new EnableAlertRuleCmd();
        cmd.setId(ruleId);

        // 执行测试
        ApiResponse<Void> apiResponse = alertController.enableRule(ruleId);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        verify(enableAlertRuleUseCase, times(1)).execute(cmd);
    }

    @Test
    public void testDisableRule() {
        // 准备测试数据
        Long ruleId = 1L;

        DisableAlertRuleCmd cmd = new DisableAlertRuleCmd();
        cmd.setId(ruleId);

        // 执行测试
        ApiResponse<Void> apiResponse = alertController.disableRule(ruleId);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        verify(disableAlertRuleUseCase, times(1)).execute(cmd);
    }

    @Test
    public void testDeleteRule() {
        // 准备测试数据
        Long ruleId = 1L;

        // 执行测试
        ApiResponse<Void> apiResponse = alertController.deleteRule(ruleId);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        verify(deleteAlertRuleUseCase, times(1)).execute(ruleId);
    }

    @Test
    public void testGetRuleById() {
        // 准备测试数据
        Long ruleId = 1L;

        AlertRuleDTO dto = new AlertRuleDTO();
        dto.setId(ruleId);
        dto.setName("testrule");

        AlertRuleResp resp = new AlertRuleResp();
        resp.setId(ruleId);
        resp.setName("testrule");

        // 模拟依赖
        when(alertRuleByIdQueryUseCase.execute(ruleId)).thenReturn(dto);
        when(alertWebConverter.toResp(dto)).thenReturn(resp);

        // 执行测试
        ApiResponse<AlertRuleResp> apiResponse = alertController.getRuleById(ruleId);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(resp, apiResponse.getData());
        verify(alertRuleByIdQueryUseCase, times(1)).execute(ruleId);
        verify(alertWebConverter, times(1)).toResp(dto);
    }

    @Test
    public void testPageRules() {
        // 准备测试数据
        AlertRulePageReq req = new AlertRulePageReq();
        req.setPageNum(1);
        req.setPageSize(10);

        AlertRulePageQry qry = new AlertRulePageQry();
        qry.setPageNum(1);
        qry.setPageSize(10);

        PageResult<AlertRuleDTO> pageResult = new PageResult<>();
        pageResult.setList(Collections.emptyList());
        pageResult.setTotal(0);
        pageResult.setPageNum(1);
        pageResult.setPageSize(10);

        PageResult<AlertRuleResp> respPageResult = new PageResult<>();
        respPageResult.setList(Collections.emptyList());
        respPageResult.setTotal(0);
        respPageResult.setPageNum(1);
        respPageResult.setPageSize(10);

        // 模拟依赖
        when(alertWebConverter.toQry(req)).thenReturn(qry);
        when(alertRulePageQueryUseCase.execute(qry)).thenReturn(pageResult);
        when(pageResult.map(alertWebConverter::toResp)).thenReturn(respPageResult);

        // 执行测试
        ApiResponse<PageResult<AlertRuleResp>> apiResponse = alertController.pageRules(req);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(respPageResult, apiResponse.getData());
        verify(alertWebConverter, times(1)).toQry(req);
        verify(alertRulePageQueryUseCase, times(1)).execute(qry);
        verify(pageResult, times(1)).map(alertWebConverter::toResp);
    }

    @Test
    public void testCreateEvent() {
        // 准备测试数据
        Long ruleId = 1L;
        Double actualValue = 100.0;

        Map<String, Object> params = new HashMap<>();
        params.put("ruleId", ruleId);
        params.put("actualValue", actualValue);

        Long eventId = 1L;

        // 模拟依赖
        when(createAlertEventUseCase.execute(params)).thenReturn(eventId);

        // 执行测试
        ApiResponse<Long> apiResponse = alertController.createEvent(ruleId, actualValue);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(eventId, apiResponse.getData());
        verify(createAlertEventUseCase, times(1)).execute(params);
    }

    @Test
    public void testResolveEvent() {
        // 准备测试数据
        Long eventId = 1L;

        ResolveAlertCmd cmd = new ResolveAlertCmd();
        cmd.setId(eventId);

        // 执行测试
        ApiResponse<Void> apiResponse = alertController.resolveEvent(eventId);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        verify(resolveAlertUseCase, times(1)).execute(cmd);
    }

    @Test
    public void testGetEventById() {
        // 准备测试数据
        Long eventId = 1L;

        AlertEventDTO dto = new AlertEventDTO();
        dto.setId(eventId);
        dto.setRuleId(1L);

        AlertEventResp resp = new AlertEventResp();
        resp.setId(eventId);
        resp.setRuleId(1L);

        // 模拟依赖
        when(alertEventByIdQueryUseCase.execute(eventId)).thenReturn(dto);
        when(alertWebConverter.toResp(dto)).thenReturn(resp);

        // 执行测试
        ApiResponse<AlertEventResp> apiResponse = alertController.getEventById(eventId);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(resp, apiResponse.getData());
        verify(alertEventByIdQueryUseCase, times(1)).execute(eventId);
        verify(alertWebConverter, times(1)).toResp(dto);
    }

    @Test
    public void testPageEvents() {
        // 准备测试数据
        AlertEventPageReq req = new AlertEventPageReq();
        req.setPageNum(1);
        req.setPageSize(10);

        int[] params = {req.getPageNum(), req.getPageSize()};

        PageResult<AlertEventDTO> pageResult = new PageResult<>();
        pageResult.setList(Collections.emptyList());
        pageResult.setTotal(0);
        pageResult.setPageNum(1);
        pageResult.setPageSize(10);

        PageResult<AlertEventResp> respPageResult = new PageResult<>();
        respPageResult.setList(Collections.emptyList());
        respPageResult.setTotal(0);
        respPageResult.setPageNum(1);
        respPageResult.setPageSize(10);

        // 模拟依赖
        when(alertEventPageQueryUseCase.execute(params)).thenReturn(pageResult);
        when(pageResult.map(alertWebConverter::toResp)).thenReturn(respPageResult);

        // 执行测试
        ApiResponse<PageResult<AlertEventResp>> apiResponse = alertController.pageEvents(req);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(respPageResult, apiResponse.getData());
        verify(alertEventPageQueryUseCase, times(1)).execute(params);
        verify(pageResult, times(1)).map(alertWebConverter::toResp);
    }
}
