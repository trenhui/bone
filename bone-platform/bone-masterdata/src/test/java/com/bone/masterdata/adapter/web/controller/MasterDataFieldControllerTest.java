package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.CreateMasterDataFieldCmd;
import com.bone.masterdata.application.command.handler.CreateMasterDataFieldHandler;
import com.bone.masterdata.application.query.handler.MasterDataFieldListQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQry;
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

class MasterDataFieldControllerTest {

    @Mock
    private CreateMasterDataFieldHandler createHandler;

    @Mock
    private MasterDataFieldListQueryHandler listQueryHandler;

    @InjectMocks
    private MasterDataFieldController masterDataFieldController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(masterDataFieldController).build();
    }

    @Test
    void testCreate() throws Exception {
        when(createHandler.handle(any(CreateMasterDataFieldCmd.class))).thenReturn(1L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/masterdata/fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"测试字段\",\"type\":\"STRING\",\"masterDataEntityId\":1,\"length\":64,\"required\":true}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
    }

    @Test
    void testList() throws Exception {
        when(listQueryHandler.handle(any(MasterDataFieldListQry.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/masterdata/fields")
                        .param("masterDataEntityId", "1"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray());
    }
}
