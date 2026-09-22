package com.bone.masterdata.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.CreateDataStandardCommand;
import com.bone.masterdata.application.command.DeleteDataStandardCommand;
import com.bone.masterdata.application.command.UpdateDataStandardCommand;
import com.bone.masterdata.application.query.dto.DataStandardDTO;
import com.bone.masterdata.application.query.qry.DataStandardPageQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.model.standard.DataStandard;
import com.bone.masterdata.domain.model.standard.valueobject.StandardFieldCode;
import com.bone.masterdata.domain.model.standard.valueobject.StandardRuleType;
import com.bone.masterdata.domain.repository.DataStandardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据标准应用服务（ADR-0028 Application Service First）。
 *
 * <p>原 {@code DataStandardCommandHandler} 与 {@code DataStandardQueryHandler} 的全部用例已内联合并到本服务。
 * 读侧领域模型经由 {@link DataStandardRepository} 的 default 方法承载（ADR-0030），应用层不直接依赖持久化 DSL。
 *
 * <p><b>不发 DomainEvent 的豁免理由（E-5.4）</b>：数据标准是纯配置型数据，创建/更新/删除均无下游需要感知的状态迁移， 聚合自身也未注册任何 {@code
 * DomainEvent}；若将来出现订阅方，改为接入 {@code publishFrom} 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class StandardApplicationService {

  private static final int MAX_PAGE_SIZE = 500;

  private final DataStandardRepository dataStandardRepository;

  @Capability(
      name = "ManageDataStandard",
      description = "数据标准管理",
      inputSchema =
          "{\"type\": \"string\", \"entityCode\": \"string\", \"fieldCode\": \"string\", \"ruleType\": \"string\", \"pattern\": \"string\", \"refCode\": \"string\", \"description\": \"string\"}",
      outputSchema = "{\"standardId\": \"long\"}",
      idempotent = false,
      cost = 3,
      retryable = false,
      timeout = 30)
  @Transactional
  public Long create(CreateDataStandardCommand cmd) {
    long existing =
        dataStandardRepository.countByEntityCodeAndFieldCode(
            cmd.getEntityCode(), StandardFieldCode.of(cmd.getFieldCode()));
    if (existing > 0) {
      throw MasterDataErrors.of(MasterDataErrorCodes.DATA_STANDARD_DUPLICATE, "数据标准已存在");
    }
    Long id = DistributedIdGenerator.generateLongId();
    DataStandard standard =
        DataStandard.create(
            id,
            cmd.getEntityCode(),
            StandardFieldCode.of(cmd.getFieldCode()),
            StandardRuleType.of(cmd.getRuleType()),
            cmd.getPattern(),
            cmd.getRefCode(),
            cmd.getDescription());
    return dataStandardRepository.save(standard);
  }

  @Transactional
  public void update(UpdateDataStandardCommand cmd) {
    DataStandard standard = dataStandardRepository.findById(cmd.getId());
    if (standard == null) {
      throw NotFoundException.of("数据标准不存在");
    }
    standard.update(
        StandardRuleType.of(cmd.getRuleType()),
        cmd.getPattern(),
        cmd.getRefCode(),
        cmd.getDescription());
    dataStandardRepository.update(standard);
  }

  @Transactional
  public void delete(DeleteDataStandardCommand cmd) {
    DataStandard standard = dataStandardRepository.findById(cmd.getId());
    if (standard == null) {
      throw NotFoundException.of("数据标准不存在");
    }
    dataStandardRepository.deleteById(cmd.getId());
  }

  @Transactional(readOnly = true)
  public PageResult<DataStandardDTO> page(DataStandardPageQuery qry) {
    int page = Math.max(1, qry.getPageNum());
    int size = Math.min(Math.max(1, qry.getPageSize()), MAX_PAGE_SIZE);
    PageResult<DataStandard> result =
        dataStandardRepository.pageByEntityCodeAndFieldCodeLike(
            qry.getEntityCode(), qry.getKeyword(), page, size);
    return PageResult.of(
        result.getRecords().stream().map(this::toDto).toList(),
        result.getTotal(),
        result.getPage(),
        result.getSize());
  }

  @Transactional(readOnly = true)
  public DataStandardDTO getById(Long id) {
    DataStandard standard = dataStandardRepository.findById(id);
    if (standard == null) {
      throw NotFoundException.of("数据标准不存在");
    }
    return toDto(standard);
  }

  @Transactional(readOnly = true)
  public java.util.List<DataStandardDTO> listByEntity(String entityCode) {
    return dataStandardRepository.findByEntityCode(entityCode).stream().map(this::toDto).toList();
  }

  private DataStandardDTO toDto(DataStandard standard) {
    return DataStandardDTO.builder()
        .id(standard.getId())
        .entityCode(standard.getEntityCode())
        .fieldCode(standard.getFieldCode().value())
        .ruleType(standard.getRuleType().getCode())
        .pattern(standard.getPattern())
        .refCode(standard.getRefCode())
        .description(standard.getDescription())
        .createdAt(standard.getCreatedAt())
        .updatedAt(standard.getUpdatedAt())
        .build();
  }
}
