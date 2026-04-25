package com.bone.masterdata.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCmd;
import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCmd;
import com.bone.masterdata.application.command.handler.CreateDataQualityRuleHandler;
import com.bone.masterdata.application.command.handler.PerformDataQualityCheckHandler;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.handler.DataQualityRuleListQueryHandler;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class DataQualityControllerTest {

    @Mock
    private CreateDataQualityRuleHandler createRuleHandler;

    @Mock
    private DataQualityRuleListQueryHandler ruleListQueryHandler;

    @Mock
    private PerformDataQualityCheckHandler performCheckHandler;

    @InjectMocks
    private DataQualityController dataQualityController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(dataQualityController).build();
    }

    @Test
    void testCreateRule() throws Exception {
        CreateDataQualityRuleCmd cmd = new CreateDataQualityRuleCmd();
        cmd.setName("测试规则");
        cmd.setDescription("测试规则描述");
        cmd.setMasterDataEntityId(1L);

        when(createRuleHandler.handle(cmd)).thenReturn(1L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/masterdata/quality/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"测试规则\",\"description\":\"测试规则描述\",\"masterDataEntityId\":1}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
    }

    @Test
    void testListRules() throws Exception {
        DataQualityRuleListQry qry = new DataQualityRuleListQry();
        when(ruleListQueryHandler.handle(qry)).thenReturn(Collections.emptyList());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/masterdata/quality/rules"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray());
    }

    @Test
    void testPerformCheck() throws Exception {
        Long masterDataEntityId = 1L;
        when(performCheckHandler.handle(any(PerformDataQualityCheckCmd.class))).thenReturn(1L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/masterdata/quality/check")
                .param("masterDataEntityId", masterDataEntityId.toString()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
    }

    @Test
    void testGetReport() throws Exception {
        Long id = 1L;

        mockMvc.perform(MockMvcRequestBuilders.get("/api/masterdata/quality/reports/{id}", id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value("质量报告内容"));
    }
}
