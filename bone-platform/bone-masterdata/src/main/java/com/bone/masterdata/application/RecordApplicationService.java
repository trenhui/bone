package com.bone.masterdata.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.command.UpdateMasterDataRecordCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.common.MasterDataProperties;
import com.bone.masterdata.domain.gateway.MasterDataExcelImportPort;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.model.record.valueobject.MasterDataRecordStatus;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主数据记录应用服务（ADR-0028 Application Service First）。
 *
 * <p>读侧领域模型由 {@link MasterDataRecordRepository} 的 default 方法承载（ADR-0030），应用层不直接依赖持久化 DSL。
 */
@Service
@RequiredArgsConstructor
public class RecordApplicationService {

  private static final int MAX_PAGE_SIZE = 500;

  private final MasterDataRecordRepository recordRepository;
  private final MasterDataEntityRepository entityRepository;
  private final MasterDataExcelImportPort excelImportPort;
  private final MasterdataDomainEventPublisher domainEventPublisher;
  private final MasterDataProperties properties;

  @Capability(
      name = "CreateMasterDataRecord",
      description = "创建主数据记录",
      inputSchema = "{\"masterDataEntityId\": \"long\", \"data\": \"object\"}",
      outputSchema = "{\"recordId\": \"long\"}",
      idempotent = false,
      cost = 2,
      retryable = true,
      timeout = 15)
  @Transactional
  public Long create(CreateMasterDataRecordCommand cmd) {
    if (entityRepository.findById(cmd.getMasterDataEntityId()) == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    checkDataSize(cmd.getData());
    Long recordId = DistributedIdGenerator.generateLongId();
    MasterDataRecord record =
        MasterDataRecord.create(recordId, cmd.getMasterDataEntityId(), cmd.getData());
    Long savedId = recordRepository.save(record);
    domainEventPublisher.publishFrom(record);
    return savedId;
  }

  @Capability(
      name = "UpdateMasterDataRecord",
      description = "更新主数据记录",
      inputSchema = "{\"id\": \"long\", \"masterDataEntityId\": \"long\", \"data\": \"object\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void update(UpdateMasterDataRecordCommand cmd) {
    MasterDataRecord record = recordRepository.findById(cmd.getId());
    if (record == null) {
      throw NotFoundException.of("主数据记录不存在");
    }
    checkDataSize(cmd.getData());
    record.update(cmd.getData());
    recordRepository.update(record);
    domainEventPublisher.publishFrom(record);
  }

  @Capability(
      name = "PublishMasterDataRecord",
      description = "发布主数据记录",
      inputSchema = "{\"id\": \"long\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 3,
      retryable = false,
      timeout = 30)
  @Transactional
  public void publish(Long id) {
    MasterDataRecord record = recordRepository.findById(id);
    if (record == null) {
      throw NotFoundException.of("主数据记录不存在");
    }
    record.publish();
    recordRepository.update(record);
    domainEventPublisher.publishFrom(record);
  }

  @Capability(
      name = "ArchiveMasterDataRecord",
      description = "归档主数据记录",
      inputSchema = "{\"id\": \"long\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void archive(Long id) {
    MasterDataRecord record = recordRepository.findById(id);
    if (record == null) {
      throw NotFoundException.of("主数据记录不存在");
    }
    record.archive();
    recordRepository.update(record);
    domainEventPublisher.publishFrom(record);
  }

  @Capability(
      name = "DeleteMasterDataRecord",
      description = "删除主数据记录",
      inputSchema = "{\"id\": \"long\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 3,
      retryable = false,
      timeout = 30)
  @Transactional
  public void delete(Long id) {
    MasterDataRecord record = recordRepository.findById(id);
    if (record == null) {
      throw NotFoundException.of("主数据记录不存在");
    }
    recordRepository.deleteById(id);
  }

  @Capability(
      name = "ImportMasterDataRecords",
      description = "批量导入主数据记录",
      inputSchema = "{\"masterDataEntityId\": \"long\", \"fileContent\": \"string\"}",
      outputSchema = "{\"recordIds\": \"array<long>\"}",
      idempotent = false,
      cost = 5,
      retryable = false,
      timeout = 60)
  @Transactional
  public List<Long> importRecords(ImportMasterDataRecordsCommand cmd) {
    List<MasterDataRecord> parsed =
        excelImportPort.parseRecords(
            cmd.getDataStream(), cmd.getOriginalFilename(), cmd.getMasterDataEntityId());
    List<Long> ids = new ArrayList<>();
    for (MasterDataRecord record : parsed) {
      ids.add(recordRepository.save(record));
      domainEventPublisher.publishFrom(record);
    }
    return ids;
  }

  @Transactional(readOnly = true)
  public PageResult<MasterDataRecordDTO> list(MasterDataRecordListQuery qry) {
    MasterDataRecordStatus status = null;
    if (qry.getStatus() != null && !qry.getStatus().isBlank()) {
      status = MasterDataRecordStatus.valueOf(qry.getStatus());
    }
    int page = Math.max(1, qry.getPageNum());
    int size = Math.min(Math.max(1, qry.getPageSize()), MAX_PAGE_SIZE);
    PageResult<MasterDataRecord> result =
        recordRepository.pageByEntityIdStatusAndKeyword(
            qry.getMasterDataEntityId(), status, qry.getKeyword(), page, size);
    return PageResult.of(
        result.getRecords().stream().map(this::toDto).toList(),
        result.getTotal(),
        result.getPage(),
        result.getSize());
  }

  @Transactional(readOnly = true)
  public MasterDataRecordDTO detail(MasterDataRecordByIdQuery qry) {
    MasterDataRecord record = recordRepository.findById(qry.getId());
    if (record == null) {
      throw new NotFoundException("主数据记录不存在");
    }
    return toDto(record);
  }

  /** 导出主数据记录为文本（原 ExportMasterDataRecordsQueryHandler，无能力元数据）。 */
  @Transactional(readOnly = true)
  public String export(Long masterDataEntityId) {
    List<MasterDataRecord> records = recordRepository.findByMasterDataEntityId(masterDataEntityId);
    if (records.size() > properties.getExportMaxRecords()) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.EXPORT_LIMIT_EXCEEDED,
          "导出记录数 " + records.size() + " 超过上限 " + properties.getExportMaxRecords());
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < records.size(); i++) {
      MasterDataRecord r = records.get(i);
      sb.append("id=").append(r.getId()).append(", data=").append(r.getData());
      if (i < records.size() - 1) {
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  private void checkDataSize(String data) {
    if (data != null && data.length() > properties.getRecordMaxSize()) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.RECORD_SIZE_EXCEEDED,
          "记录数据超过 " + properties.getRecordMaxSize() + " 字符上限");
    }
  }

  private MasterDataRecordDTO toDto(MasterDataRecord record) {
    return MasterDataRecordDTO.builder()
        .id(record.getId())
        .masterDataEntityId(record.getMasterDataEntityId())
        .data(record.getData())
        .status(record.getStatus() != null ? record.getStatus().name() : null)
        .createdAt(record.getCreatedAt())
        .updatedAt(record.getUpdatedAt())
        .publishTime(record.getPublishTime())
        .build();
  }
}
