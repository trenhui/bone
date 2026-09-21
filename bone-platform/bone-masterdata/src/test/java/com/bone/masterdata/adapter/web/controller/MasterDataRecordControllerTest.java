package com.bone.masterdata.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.masterdata.application.RecordApplicationService;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
public class MasterDataRecordControllerTest {

  @Mock private RecordApplicationService recordService;

  @InjectMocks private MasterDataRecordController masterDataRecordController;

  @Test
  public void testImportRecords() throws Exception {
    Long masterDataEntityId = 1L;
    List<Long> recordIds = Collections.singletonList(1L);

    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.xlsx", "application/octet-stream", new ByteArrayInputStream(new byte[0]));

    when(recordService.importRecords(any())).thenReturn(recordIds);

    ApiResponse<List<Long>> apiResponse =
        masterDataRecordController.importRecords(masterDataEntityId, file);

    assertEquals(true, apiResponse.isSuccess());
    assertEquals(recordIds, apiResponse.getData());
    verify(recordService, times(1)).importRecords(any());
  }

  @Test
  public void testList() {
    MasterDataRecordListQuery qry = new MasterDataRecordListQuery();
    qry.setMasterDataEntityId(1L);
    qry.setPageNum(1);
    qry.setPageSize(10);

    PageResult<MasterDataRecordDTO> pageResult = PageResult.of(Collections.emptyList(), 0L, 1, 10);

    when(recordService.list(qry)).thenReturn(pageResult);

    ApiResponse<PageResult<MasterDataRecordDTO>> apiResponse =
        masterDataRecordController.list(1L, qry);

    assertEquals(true, apiResponse.isSuccess());
    assertEquals(pageResult, apiResponse.getData());
    verify(recordService, times(1)).list(qry);
  }

  @Test
  public void testPublish() {
    Long recordId = 1L;

    ApiResponse<Void> apiResponse = masterDataRecordController.publish(recordId);

    assertEquals(true, apiResponse.isSuccess());
    verify(recordService, times(1)).publish(recordId);
  }

  @Test
  public void testExport() {
    Long masterDataEntityId = 1L;
    when(recordService.export(masterDataEntityId)).thenReturn("[]");

    ApiResponse<String> apiResponse = masterDataRecordController.export(masterDataEntityId);

    assertEquals(true, apiResponse.isSuccess());
    assertEquals("[]", apiResponse.getData());
    verify(recordService).export(masterDataEntityId);
  }
}
