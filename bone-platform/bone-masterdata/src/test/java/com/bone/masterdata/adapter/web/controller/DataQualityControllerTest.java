package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCmd;
import com.bone.masterdata.application.command.handler.CreateDataQualityRuleHandler;
import com.bone.masterdata.application.command.handler.PerformDataQualityCheckHandler;
import com.bone.masterdata.application.query.handler.DataQualityRuleListQueryHandler;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        when(createRuleHandler.handle(any())).thenReturn(1L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/masterdata/quality/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"测试规则\",\"type\":\"NOT_NULL\",\"expression\":\"data != null\",\"masterDataEntityId\":1,\"severity\":\"HIGH\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
    }

    @Test
    void testListRules() throws Exception {
        when(ruleListQueryHandler.handle(any(DataQualityRuleListQry.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/masterdata/quality/rules")
                        .param("masterDataEntityId", "1"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray());
    }

    @Test
    void testPerformCheck() throws Exception {
        when(performCheckHandler.handle(any(PerformDataQualityCheckCmd.class))).thenReturn(1L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/masterdata/quality/check")
                        .param("masterDataEntityId", "1"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
    }
}
