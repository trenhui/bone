package com.bone.masterdata.adapter.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bone.masterdata.adapter.web.converter.MasterDataFieldWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataFieldReq;
import com.bone.masterdata.application.FieldApplicationService;
import com.bone.masterdata.application.command.CreateMasterDataFieldCommand;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQuery;
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
class MasterDataFieldControllerTest {

  @Mock private FieldApplicationService fieldService;

  @Mock private MasterDataFieldWebConverter converter;

  @InjectMocks private MasterDataFieldController masterDataFieldController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(masterDataFieldController).build();
  }

  @Test
  void testCreate() throws Exception {
    CreateMasterDataFieldCommand command = new CreateMasterDataFieldCommand();
    command.setMasterDataEntityId(1L);
    when(converter.toCommand(any(CreateMasterDataFieldReq.class))).thenReturn(command);
    when(fieldService.create(any(CreateMasterDataFieldCommand.class))).thenReturn(1L);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/masterdata/entities/1/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"测试字段\",\"type\":\"STRING\",\"masterDataEntityId\":1,\"length\":64,\"required\":true}"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
        .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
  }

  @Test
  void testList() throws Exception {
    when(fieldService.list(any(MasterDataFieldListQuery.class)))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/v1/masterdata/entities/1/fields"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
        .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray());
  }
}
