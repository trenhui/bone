package com.bone.masterdata.adapter.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bone.masterdata.adapter.web.converter.DataQualityWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateDataQualityRuleReq;
import com.bone.masterdata.application.QualityApplicationService;
import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCommand;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQuery;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DataQualityControllerTest {

  @Mock private QualityApplicationService qualityService;

  @Mock private DataQualityWebConverter converter;

  @InjectMocks private DataQualityController dataQualityController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(dataQualityController).build();
  }

  @Test
  void testCreateRule() throws Exception {
    CreateDataQualityRuleCommand command = new CreateDataQualityRuleCommand();
    command.setMasterDataEntityId(1L);
    when(converter.toCommand(any(CreateDataQualityRuleReq.class))).thenReturn(command);
    when(qualityService.createRule(any(CreateDataQualityRuleCommand.class))).thenReturn(1L);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/masterdata/quality/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"测试规则\",\"type\":\"NOT_NULL\",\"expression\":\"data != null\",\"masterDataEntityId\":1,\"severity\":\"HIGH\"}"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
        .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
  }

  @Test
  void testListRules() throws Exception {
    when(qualityService.ruleList(any(DataQualityRuleListQuery.class)))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/masterdata/quality/rules")
                .param("masterDataEntityId", "1"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
        .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray());
  }

  @Test
  void testPerformCheck() throws Exception {
    when(qualityService.performCheck(any(PerformDataQualityCheckCommand.class))).thenReturn(1L);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/masterdata/quality/check")
                .param("masterDataEntityId", "1"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
        .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
  }
}
