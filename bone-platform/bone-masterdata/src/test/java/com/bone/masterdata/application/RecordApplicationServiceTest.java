package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.masterdata.application.command.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.application.query.dto.ImportResultDTO;
import com.bone.masterdata.common.MasterDataProperties;
import com.bone.masterdata.domain.gateway.MasterDataExcelImportPort;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordVersionRepository;
import com.bone.masterdata.domain.repository.ReferenceSetRepository;
import com.bone.masterdata.domain.repository.ReferenceValueRepository;
import com.bone.masterdata.domain.repository.TenantReferenceValueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordApplicationServiceTest {

  @Mock private MasterDataRecordRepository recordRepository;
  @Mock private MasterDataEntityRepository entityRepository;
  @Mock private MasterDataFieldRepository fieldRepository;
  @Mock private MasterDataRecordVersionRepository versionRepository;
  @Mock private MasterDataExcelImportPort excelImportPort;
  @Mock private MasterdataDomainEventPublisher domainEventPublisher;
  @Mock private MasterDataProperties properties;
  @Mock private ReferenceSetRepository referenceSetRepository;
  @Mock private ReferenceValueRepository referenceValueRepository;
  @Mock private TenantReferenceValueRepository tenantReferenceValueRepository;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private RecordApplicationService service;

  @Test
  void createPublishesDomainEvent() {
    when(properties.getRecordMaxSize()).thenReturn(1024);
    when(entityRepository.findById(1L)).thenReturn(mock(MasterDataEntity.class));
    when(recordRepository.save(any())).thenReturn(22L);

    CreateMasterDataRecordCommand cmd = new CreateMasterDataRecordCommand();
    cmd.setMasterDataEntityId(1L);
    cmd.setData("{\"name\":\"x\"}");

    assertEquals(22L, service.create(cmd));
    verify(domainEventPublisher).publishFrom(any(MasterDataRecord.class));
  }

  /** 导入直接用端口解析出的聚合落库，不再二次构造（曾重复生成 ID 并丢掉解析结果）。 */
  @Test
  void importRecordsPersistsParsedRecords() {
    when(properties.getRecordMaxSize()).thenReturn(1024);
    MasterDataRecord parsed = MasterDataRecord.create(33L, 1L, "{\"name\":\"x\"}");
    when(excelImportPort.parseRecords(any(), any(), any())).thenReturn(List.of(parsed));
    when(recordRepository.save(parsed)).thenReturn(33L);

    ImportMasterDataRecordsCommand cmd = new ImportMasterDataRecordsCommand();
    cmd.setMasterDataEntityId(1L);
    cmd.setOriginalFilename("records.xlsx");
    cmd.setDataStream(new ByteArrayInputStream(new byte[0]));

    // 导入接口已改为「部分成功」语义：返回 ImportResultDTO（total/successCount/recordIds/failures），
    // 不再直接返回 ID 列表（任一行失败不再整批回滚）。
    ImportResultDTO result = service.importRecords(cmd);
    assertEquals(1, result.getTotal());
    assertEquals(1, result.getSuccessCount());
    assertEquals(List.of(33L), result.getRecordIds());
  }
}
