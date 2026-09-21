package com.bone.masterdata.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCommand;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import com.bone.masterdata.application.query.qry.MasterDataRecordPageQuery;
import com.bone.masterdata.domain.gateway.MasterDataExcelImportPort;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordStatus;
import com.bone.masterdata.domain.record.MasterDataRecord;
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
 * <p>原 {@code application/command/handler/*Record*} 与 {@code application/query/handler/*Record*}
 * 的全部用例已内联合并到本服务。 读侧领域模型经由 {@link MasterDataRecordRepository} 的 default 方法承载（ADR-0030），应用层不直接依赖持久化
 * DSL。
 */
@Service
@RequiredArgsConstructor
public class RecordApplicationService {

  private final MasterDataRecordRepository recordRepository;
  private final MasterDataEntityRepository entityRepository;
  private final MasterDataExcelImportPort excelImportPort;

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
    Long recordId = DistributedIdGenerator.generateLongId();
    MasterDataRecord record =
        MasterDataRecord.create(recordId, cmd.getMasterDataEntityId(), cmd.getData());
    return recordRepository.save(record);
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
    record.update(cmd.getData());
    recordRepository.update(record);
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
    for (MasterDataRecord p : parsed) {
      MasterDataRecord record =
          MasterDataRecord.create(
              DistributedIdGenerator.generateLongId(), cmd.getMasterDataEntityId(), p.getData());
      ids.add(recordRepository.save(record));
    }
    return ids;
  }

  @Transactional(readOnly = true)
  public PageResult<MasterDataRecordDTO> page(MasterDataRecordPageQuery qry) {
    int page = qry.getPageNum() != null && qry.getPageNum() > 0 ? qry.getPageNum() : 1;
    int size = qry.getPageSize() != null && qry.getPageSize() > 0 ? qry.getPageSize() : 10;
    PageResult<MasterDataRecord> result =
        recordRepository.pageByEntityId(qry.getMasterDataEntityId(), page, size);
    return PageResult.of(
        result.getRecords().stream().map(this::toDto).toList(),
        result.getTotal(),
        result.getPage(),
        result.getSize());
  }

  @Transactional(readOnly = true)
  public PageResult<MasterDataRecordDTO> list(MasterDataRecordListQuery qry) {
    MasterDataRecordStatus status = null;
    if (qry.getStatus() != null && !qry.getStatus().isBlank()) {
      status = MasterDataRecordStatus.valueOf(qry.getStatus());
    }
    int page = Math.max(1, qry.getPageNum());
    int size = Math.max(1, qry.getPageSize());
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
