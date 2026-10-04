package com.bone.masterdata.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.command.UpdateMasterDataRecordCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.application.query.dto.ImportFailureDTO;
import com.bone.masterdata.application.query.dto.ImportResultDTO;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.common.MasterDataProperties;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.gateway.MasterDataExcelImportPort;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.MasterDataField;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.model.record.MasterDataRecordVersion;
import com.bone.masterdata.domain.model.record.valueobject.MasterDataRecordStatus;
import com.bone.masterdata.domain.model.reference.ReferenceSet;
import com.bone.masterdata.domain.model.reference.ReferenceValue;
import com.bone.masterdata.domain.model.reference.TenantReferenceValue;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordVersionRepository;
import com.bone.masterdata.domain.repository.ReferenceSetRepository;
import com.bone.masterdata.domain.repository.ReferenceValueRepository;
import com.bone.masterdata.domain.repository.TenantReferenceValueRepository;
import com.bone.masterdata.domain.service.record.RecordDataValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主数据记录应用服务（ADR-0028 Application Service First）。
 *
 * <p>读侧领域模型由 {@link MasterDataRecordRepository} 的 default 方法承载（ADR-0030），应用层不直接依赖持久化 DSL。 审批发布（G5 /
 * UC-T7）：六态状态机 + SoD（审批人≠提交人）+ 工作流门禁（L2/L3 实体未审批不得发布）+ 版本快照。
 */
@Service
@RequiredArgsConstructor
public class RecordApplicationService {

  private static final int MAX_PAGE_SIZE = 500;

  private final MasterDataRecordRepository recordRepository;
  private final MasterDataEntityRepository entityRepository;
  private final MasterDataFieldRepository fieldRepository;
  private final MasterDataRecordVersionRepository versionRepository;
  private final MasterDataExcelImportPort excelImportPort;
  private final CurrentUserPort currentUserPort;
  private final MasterdataDomainEventPublisher domainEventPublisher;
  private final MasterDataProperties properties;
  private final ReferenceSetRepository referenceSetRepository;
  private final ReferenceValueRepository referenceValueRepository;
  private final TenantReferenceValueRepository tenantReferenceValueRepository;
  private final RecordDataValidator recordDataValidator = new RecordDataValidator();
  private final ObjectMapper objectMapper;

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
    validateAgainstFieldDefinitions(cmd.getMasterDataEntityId(), cmd.getData());
    assertRecordCodeUnique(cmd.getMasterDataEntityId(), cmd.getRecordCode(), null);
    Long recordId = DistributedIdGenerator.generateLongId();
    MasterDataRecord record =
        MasterDataRecord.create(
            recordId,
            cmd.getMasterDataEntityId(),
            cmd.getRecordCode(),
            cmd.getDisplayName(),
            cmd.getData());
    assignEffectiveWindow(record, cmd.getEffectiveFrom(), cmd.getEffectiveTo());
    Long savedId = recordRepository.save(record);
    domainEventPublisher.publishFrom(record);
    return savedId;
  }

  /**
   * 业务编码唯一性守卫（租户+实体内）。
   *
   * <p>不能只依赖 DB 唯一索引：撞约束会抛 DataIntegrityViolationException 被兜成 500， 前端只能看到"服务器错误"。应用层先查一次，才能返回 409
   * + 明确业务码。
   *
   * @param excludeRecordId 更新场景排除自身（不改编码时不应被自己判为重码）
   */
  private void assertRecordCodeUnique(Long entityId, String recordCode, Long excludeRecordId) {
    if (entityId == null || recordCode == null || recordCode.isBlank()) {
      return;
    }
    List<MasterDataRecord> existing =
        recordRepository.findByEntityIdAndRecordCode(entityId, recordCode.trim());
    boolean clash =
        existing.stream()
            .anyMatch(r -> excludeRecordId == null || !r.getId().equals(excludeRecordId));
    if (clash) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.RECORD_CODE_DUPLICATE, "记录编码[" + recordCode + "]在该实体下已存在");
    }
  }

  /** 生效期窗口赋值，非法窗口在写入前拦截（避免落库后才发现数据自相矛盾）。 */
  private void assignEffectiveWindow(
      MasterDataRecord record, LocalDateTime from, LocalDateTime to) {
    if (from == null && to == null) {
      return;
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.RECORD_EFFECTIVE_WINDOW_INVALID,
          "生效开始时间 " + from + " 不得晚于结束时间 " + to);
    }
    record.assignEffectiveWindow(from, to);
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
    validateAgainstFieldDefinitions(record.getMasterDataEntityId(), cmd.getData());
    assertRecordCodeUnique(record.getMasterDataEntityId(), cmd.getRecordCode(), record.getId());
    // 发布态「补登业务主键」是被允许的治理动作：只改编码/名称/生效期、不动 data。
    // 但凡 data 变动，仍走 updateData → 领域层拒绝（已发布记录须先走变更审批）。
    boolean dataChanged =
        cmd.getData() != null && !java.util.Objects.equals(record.getData(), cmd.getData());
    if (!dataChanged && record.getStatus() == MasterDataRecordStatus.PUBLISHED) {
      record.completeBusinessKey(cmd.getRecordCode(), cmd.getDisplayName());
    } else {
      record.updateData(cmd.getRecordCode(), cmd.getDisplayName(), cmd.getData());
    }
    assignEffectiveWindow(record, cmd.getEffectiveFrom(), cmd.getEffectiveTo());
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
    MasterDataRecord record = requireRecord(id);
    // 工作流门禁（UC-T7）：启用了审批流的实体（L2/L3 治理等级），记录必须先经审批通过
    MasterDataEntity entity = entityRepository.findById(record.getMasterDataEntityId());
    boolean workflowEnabled = entity != null && Boolean.TRUE.equals(entity.getWorkflowEnabled());
    if (workflowEnabled && record.getStatus() != MasterDataRecordStatus.APPROVED) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.WORKFLOW_APPROVAL_REQUIRED,
          "该实体启用了审批流，记录须先审批通过（当前: " + record.getStatus() + "）");
    }
    record.publish();
    recordRepository.update(record);
    snapshotVersion(record, null, null);
    domainEventPublisher.publishFrom(record);
  }

  /** 提交审批（UC-T7）：记录提交人，供 SoD 校验。 */
  @Capability(
      name = "SubmitRecordForApproval",
      description = "提交主数据记录审批",
      inputSchema = "{\"id\": \"long\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void submitForApproval(Long id) {
    MasterDataRecord record = requireRecord(id);
    record.submitForApproval();
    record.markSubmittedBy(currentUserPort.requireUserId());
    recordRepository.update(record);
    domainEventPublisher.publishFrom(record);
  }

  /** 审批通过（UC-T7）：SoD 硬校验——审批人不得为提交人；通过后写版本快照。 */
  @Capability(
      name = "ApproveRecord",
      description = "审批通过主数据记录",
      inputSchema = "{\"id\": \"long\", \"comment\": \"string\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 2,
      retryable = false,
      timeout = 15)
  @Transactional
  public void approve(Long id, String comment) {
    MasterDataRecord record = requireRecord(id);
    Long approverId = currentUserPort.requireUserId();
    if (record.getSubmittedBy() != null && record.getSubmittedBy().equals(approverId)) {
      throw MasterDataErrors.of(MasterDataErrorCodes.SOD_VIOLATION, "审批人不得为提交人（SoD）");
    }
    record.approve();
    recordRepository.update(record);
    snapshotVersion(record, comment, approverId);
    domainEventPublisher.publishFrom(record);
  }

  /** 审批驳回（UC-T7）：退回草稿待修改。 */
  @Capability(
      name = "RejectRecord",
      description = "驳回主数据记录审批",
      inputSchema = "{\"id\": \"long\", \"comment\": \"string\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void reject(Long id, String comment) {
    MasterDataRecord record = requireRecord(id);
    Long approverId = currentUserPort.requireUserId();
    if (record.getSubmittedBy() != null && record.getSubmittedBy().equals(approverId)) {
      throw MasterDataErrors.of(MasterDataErrorCodes.SOD_VIOLATION, "审批人不得为提交人（SoD）");
    }
    record.reject();
    recordRepository.update(record);
    domainEventPublisher.publishFrom(record);
  }

  /** 记录版本历史（UC-T7 追溯）。 */
  @Transactional(readOnly = true)
  public List<MasterDataRecordVersion> versions(Long recordId) {
    requireRecord(recordId);
    return versionRepository.findByRecordId(recordId).stream()
        .sorted((a, b) -> Integer.compare(b.getVersionNumber(), a.getVersionNumber()))
        .toList();
  }

  /** 发布 / 审批通过时冻结版本快照（幂等：同版本号不重复写）。 */
  private void snapshotVersion(MasterDataRecord record, String comment, Long approverId) {
    if (versionRepository.countByRecordIdAndVersion(record.getId(), record.getVersionNumber())
        > 0) {
      return;
    }
    versionRepository.insert(
        MasterDataRecordVersion.snapshot(
            DistributedIdGenerator.generateLongId(),
            record.getId(),
            record.getVersionNumber(),
            record.getData(),
            record.getStatus().name(),
            comment,
            approverId,
            currentUserPort.currentUserId()));
  }

  private MasterDataRecord requireRecord(Long id) {
    MasterDataRecord record = recordRepository.findById(id);
    if (record == null) {
      throw NotFoundException.of("主数据记录不存在");
    }
    return record;
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
      outputSchema =
          "{\"total\": \"int\", \"successCount\": \"int\", \"failureCount\": \"int\","
              + " \"recordIds\": \"array<long>\","
              + " \"failures\": \"array<{rowNumber:int,recordCode:string,reason:string}>\"}",
      idempotent = false,
      cost = 5,
      retryable = false,
      timeout = 60)
  public ImportResultDTO importRecords(ImportMasterDataRecordsCommand cmd) {
    List<MasterDataRecord> parsed =
        excelImportPort.parseRecords(
            cmd.getDataStream(), cmd.getOriginalFilename(), cmd.getMasterDataEntityId());
    // 真实场景：ERP/上游系统周期性全量同步，同一批编码反复导入。默认 FAIL 保持既有语义；
    // duplicateStrategy=UPDATE 时按业务编码做幂等 upsert，重复行更新而非报错。
    boolean upsert = "UPDATE".equalsIgnoreCase(cmd.getDuplicateStrategy());
    List<Long> ids = new ArrayList<>();
    List<ImportFailureDTO> failures = new ArrayList<>();
    int updated = 0;
    for (int i = 0; i < parsed.size(); i++) {
      MasterDataRecord record = parsed.get(i);
      int rowNumber = i + 1;
      try {
        checkDataSize(record.getData());
        validateAgainstFieldDefinitions(cmd.getMasterDataEntityId(), record.getData());
        if (upsert && record.getRecordCode() != null) {
          List<MasterDataRecord> existing =
              recordRepository.findByEntityIdAndRecordCode(
                  cmd.getMasterDataEntityId(), record.getRecordCode());
          if (!existing.isEmpty()) {
            upsertExisting(existing.get(0), record);
            updated++;
            continue;
          }
        }
        assertRecordCodeUnique(cmd.getMasterDataEntityId(), record.getRecordCode(), null);
        ids.add(recordRepository.save(record));
        domainEventPublisher.publishFrom(record);
      } catch (RuntimeException ex) {
        // 真实场景：ERP 批量同步 1000 条里混进 2 条脏数据，绝不能整批回滚。
        // 逐行隔离失败原因，成功行照常入库，让业务方拿到"哪些行失败、为什么"的可执行清单。
        failures.add(
            ImportFailureDTO.builder()
                .rowNumber(rowNumber)
                .recordCode(record.getRecordCode())
                .reason(ex.getMessage())
                .build());
      }
    }
    return ImportResultDTO.builder()
        .total(parsed.size())
        .successCount(ids.size())
        .updatedCount(updated)
        .failureCount(failures.size())
        .recordIds(ids)
        .failures(failures)
        .build();
  }

  /**
   * UPDATE 策略下的编码命中更新（应用层 upsert，不依赖 DB 的 INSERT..ON DUPLICATE）。
   *
   * <p>已发布记录的数据变更仍走领域门禁：data 有变化直接判失败（须变更审批）， 仅补登编码/名称/生效期等元数据时放行。
   */
  private void upsertExisting(MasterDataRecord target, MasterDataRecord incoming) {
    boolean dataChanged = !java.util.Objects.equals(target.getData(), incoming.getData());
    if (target.getStatus() == MasterDataRecordStatus.PUBLISHED && dataChanged) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.RECORD_IMMUTABLE_DATA,
          "记录[" + target.getRecordCode() + "]已发布，数据变更须走变更审批，导入行跳过");
    }
    if (dataChanged) {
      target.updateData(incoming.getRecordCode(), incoming.getDisplayName(), incoming.getData());
    } else {
      target.completeBusinessKey(incoming.getRecordCode(), incoming.getDisplayName());
    }
    if (incoming.getEffectiveFrom() != null || incoming.getEffectiveTo() != null) {
      target.assignEffectiveWindow(incoming.getEffectiveFrom(), incoming.getEffectiveTo());
    }
    recordRepository.update(target);
    domainEventPublisher.publishFrom(target);
  }

  @Transactional(readOnly = true)
  public PageResult<MasterDataRecordDTO> list(MasterDataRecordListQuery qry) {
    MasterDataRecordStatus status = null;
    if (qry.getStatus() != null && !qry.getStatus().isBlank()) {
      status = MasterDataRecordStatus.valueOf(qry.getStatus());
    }
    int page = Math.max(1, qry.getPage());
    int size = Math.min(Math.max(1, qry.getSize()), MAX_PAGE_SIZE);
    PageResult<MasterDataRecord> result =
        recordRepository.pageByEntityIdStatusAndKeyword(
            qry.getMasterDataEntityId(),
            status,
            qry.getKeyword(),
            qry.getOnlyCurrent(),
            page,
            size);
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

  /**
   * 按业务编码点查当前生效记录（下游按业务键消费的主路径）。
   *
   * <p>真实场景：blueprint / 外部系统下单时按商品编码取价，此前只能整实体全量拉取 + 内存匹配。 点查契约把「已发布 + 当前版本 + 生效窗口含此刻」三道门禁收敛到主数据侧，
   * 过期价格在这里就被拦下，消费方拿到的一定是现在能用的那一条。未命中返回 empty（不区分"不存在"与"未生效"）。
   */
  @Transactional(readOnly = true)
  public Optional<MasterDataRecordDTO> findByBusinessKey(Long masterDataEntityId, String code) {
    if (masterDataEntityId == null || code == null || code.isBlank()) {
      return Optional.empty();
    }
    return recordRepository.findByEntityIdAndRecordCode(masterDataEntityId, code.trim()).stream()
        .filter(r -> r.getStatus() == MasterDataRecordStatus.PUBLISHED)
        .filter(r -> Boolean.TRUE.equals(r.getIsCurrent()))
        .filter(r -> r.isEffectiveAt(LocalDateTime.now()))
        .findFirst()
        .map(this::toDto);
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

  /**
   * 写入时按字段定义校验记录数据（真实场景第一道防线）。
   *
   * <p>实体尚未建模字段时直接放行（采集阶段允许先录后治），一旦建模即刻生效， 避免"字段定义只用于展示"的割裂。
   */
  private void validateAgainstFieldDefinitions(Long entityId, String data) {
    List<MasterDataField> fields = fieldRepository.findByMasterDataEntityId(entityId);
    if (fields.isEmpty()) {
      return;
    }
    Map<String, Object> parsed = parseData(data);
    Map<String, Set<String>> allowedValues = resolveAllowedValues(fields);
    List<String> violations = recordDataValidator.validate(fields, parsed, allowedValues);
    if (!violations.isEmpty()) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.RECORD_FIELD_VALIDATION_FAILED, String.join("；", violations));
    }
  }

  private Map<String, Object> parseData(String data) {
    if (data == null || data.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.RECORD_DATA_PARSE_FAILED, "记录 data 不是合法 JSON 对象");
    }
  }

  /**
   * 按 field code 关联参考数据值域，得到「字段 → 允许值集合」。
   *
   * <p>约定：值域 {@code set_code} 与字段 {@code code} 同名即建立绑定（如字段 currency → 值域 CURRENCY），租户私有扩展值以 overlay
   * 方式并入。
   */
  private Map<String, Set<String>> resolveAllowedValues(List<MasterDataField> fields) {
    Map<String, String> fieldCodeByNormalized = new HashMap<>();
    for (MasterDataField field : fields) {
      if (field.getCode() == null) {
        continue;
      }
      String code = field.getCode().value();
      fieldCodeByNormalized.put(code.toUpperCase(Locale.ROOT), code);
    }
    if (fieldCodeByNormalized.isEmpty()) {
      return Map.of();
    }
    Map<String, Set<String>> allowed = new HashMap<>();
    for (ReferenceSet set : referenceSetRepository.findByStatus("PUBLISHED")) {
      if (set.getSetCode() == null) {
        continue;
      }
      String fieldCode = fieldCodeByNormalized.get(set.getSetCode().toUpperCase(Locale.ROOT));
      if (fieldCode == null) {
        continue;
      }
      Set<String> values = new HashSet<>();
      for (ReferenceValue v : referenceValueRepository.findBySetId(set.getId())) {
        if (Boolean.TRUE.equals(v.getEnabled()) && v.getValueCode() != null) {
          values.add(v.getValueCode());
        }
      }
      for (TenantReferenceValue v : tenantReferenceValueRepository.findBySetId(set.getId())) {
        if (Boolean.TRUE.equals(v.getEnabled()) && v.getValueCode() != null) {
          values.add(v.getValueCode());
        }
      }
      if (!values.isEmpty()) {
        allowed.put(fieldCode, values);
      }
    }
    return allowed;
  }

  private MasterDataRecordDTO toDto(MasterDataRecord record) {
    return MasterDataRecordDTO.builder()
        .id(record.getId())
        .masterDataEntityId(record.getMasterDataEntityId())
        .data(record.getData())
        .status(record.getStatus() != null ? record.getStatus().name() : null)
        .recordCode(record.getRecordCode())
        .displayName(record.getDisplayName())
        .effectiveFrom(record.getEffectiveFrom())
        .effectiveTo(record.getEffectiveTo())
        // current 语义 = 当前版本 且 此刻在生效期内：过期记录不再对外宣称"当前生效"，
        // 否则消费方按 current=true 取到一条已过期价格（isCurrent 落库后不会随时间自动翻转）。
        .current(
            Boolean.TRUE.equals(record.getIsCurrent()) && record.isEffectiveAt(LocalDateTime.now()))
        .createdAt(record.getCreatedAt())
        .updatedAt(record.getUpdatedAt())
        .publishTime(record.getPublishTime())
        .build();
  }
}
