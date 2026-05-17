package com.bone.masterdata.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCmd;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQry;
import com.bone.masterdata.application.usecase.standard.ImportMasterDataRecordsUseCase;
import com.bone.masterdata.application.usecase.standard.PublishMasterDataRecordUseCase;
import com.bone.masterdata.application.query.handler.ExportMasterDataRecordsQueryHandler;
import com.bone.masterdata.application.usecase.standard.MasterDataRecordListQueryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class MasterDataRecordControllerTest {

    @Mock
    private ImportMasterDataRecordsUseCase importMasterDataRecordsUseCase;

    @Mock
    private MasterDataRecordListQueryUseCase masterDataRecordListQueryUseCase;

    @Mock
    private PublishMasterDataRecordUseCase publishMasterDataRecordUseCase;

    @Mock
    private ExportMasterDataRecordsQueryHandler exportMasterDataRecordsQueryHandler;

    @Mock
    private MultipartFile file;

    @InjectMocks
    private MasterDataRecordController masterDataRecordController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testImportRecords() {
        // 准备测试数据
        Long masterDataEntityId = 1L;

        ImportMasterDataRecordsCmd cmd = new ImportMasterDataRecordsCmd();
        cmd.setMasterDataEntityId(masterDataEntityId);
        cmd.setFile(file);

        List<Long> recordIds = Collections.singletonList(1L);

        // 模拟依赖
        when(importMasterDataRecordsUseCase.execute(cmd)).thenReturn(recordIds);

        // 执行测试
        ApiResponse<List<Long>> apiResponse = masterDataRecordController.importRecords(masterDataEntityId, file);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(recordIds, apiResponse.getData());
        verify(importMasterDataRecordsUseCase, times(1)).execute(cmd);
    }

    @Test
    public void testList() {
        // 准备测试数据
        MasterDataRecordListQry qry = new MasterDataRecordListQry();
        qry.setMasterDataEntityId(1L);
        qry.setPageNum(1);
        qry.setPageSize(10);

        PageResult<MasterDataRecordDTO> pageResult = PageResult.of(Collections.emptyList(), 0, 1, 10);

        // 模拟依赖
        when(masterDataRecordListQueryUseCase.execute(qry)).thenReturn(pageResult);

        // 执行测试
        ApiResponse<PageResult<MasterDataRecordDTO>> apiResponse = masterDataRecordController.list(qry);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        assertEquals(pageResult, apiResponse.getData());
        verify(masterDataRecordListQueryUseCase, times(1)).execute(qry);
    }

    @Test
    public void testPublish() {
        // 准备测试数据
        Long recordId = 1L;

        // 执行测试
        ApiResponse<Void> apiResponse = masterDataRecordController.publish(recordId);

        // 验证结果
        assertEquals(true, apiResponse.isSuccess());
        verify(publishMasterDataRecordUseCase, times(1)).execute(recordId);
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
