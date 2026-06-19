package com.bone.masterdata.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.masterdata.application.command.handler.ImportMasterDataRecordsHandler;
import com.bone.masterdata.application.command.handler.PublishMasterDataRecordHandler;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.handler.ExportMasterDataRecordsQueryHandler;
import com.bone.masterdata.application.query.handler.MasterDataRecordListQueryHandler;
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

  @Mock private ImportMasterDataRecordsHandler importMasterDataRecordsHandler;

  @Mock private MasterDataRecordListQueryHandler masterDataRecordListQueryHandler;

  @Mock private PublishMasterDataRecordHandler publishMasterDataRecordHandler;

  @Mock private ExportMasterDataRecordsQueryHandler exportMasterDataRecordsQueryHandler;

  @InjectMocks private MasterDataRecordController masterDataRecordController;

  @Test
  public void testImportRecords() throws Exception {
    // 准备测试数据
    Long masterDataEntityId = 1L;
    List<Long> recordIds = Collections.singletonList(1L);

    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.xlsx", "application/octet-stream", new ByteArrayInputStream(new byte[0]));

    // 模拟依赖
    when(importMasterDataRecordsHandler.handle(any())).thenReturn(recordIds);

    // 执行测试
    ApiResponse<List<Long>> apiResponse =
        masterDataRecordController.importRecords(masterDataEntityId, file);

    // 验证结果
    assertEquals(true, apiResponse.isSuccess());
    assertEquals(recordIds, apiResponse.getData());
    verify(importMasterDataRecordsHandler, times(1)).handle(any());
  }

  @Test
  public void testList() {
    // 准备测试数据
    MasterDataRecordListQuery qry = new MasterDataRecordListQuery();
    qry.setMasterDataEntityId(1L);
    qry.setPageNum(1);
    qry.setPageSize(10);

    PageResult<MasterDataRecordDTO> pageResult = PageResult.of(Collections.emptyList(), 0L, 1, 10);

    // 模拟依赖
    when(masterDataRecordListQueryHandler.handle(qry)).thenReturn(pageResult);

    // 执行测试
    ApiResponse<PageResult<MasterDataRecordDTO>> apiResponse =
        masterDataRecordController.list(1L, qry);

    // 验证结果
    assertEquals(true, apiResponse.isSuccess());
    assertEquals(pageResult, apiResponse.getData());
    verify(masterDataRecordListQueryHandler, times(1)).handle(qry);
  }

  @Test
  public void testPublish() {
    // 准备测试数据
    Long recordId = 1L;

    // 执行测试
    ApiResponse<Void> apiResponse = masterDataRecordController.publish(recordId);

    // 验证结果
    assertEquals(true, apiResponse.isSuccess());
    verify(publishMasterDataRecordHandler, times(1)).handle(recordId);
  }

  @Test
  public void testExport() {
    Long masterDataEntityId = 1L;
    when(exportMasterDataRecordsQueryHandler.handle(masterDataEntityId)).thenReturn("[]");

    ApiResponse<String> apiResponse = masterDataRecordController.export(masterDataEntityId);

    assertEquals(true, apiResponse.isSuccess());
    assertEquals("[]", apiResponse.getData());
    verify(exportMasterDataRecordsQueryHandler).handle(masterDataEntityId);
  }
}
