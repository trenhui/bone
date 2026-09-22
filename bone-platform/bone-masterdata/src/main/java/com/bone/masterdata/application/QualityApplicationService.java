package com.bone.masterdata.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCommand;
import com.bone.masterdata.application.command.cmd.UpdateDataQualityRuleCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.dto.QualityCheckDTO;
import com.bone.masterdata.application.query.dto.QualityReportDTO;
import com.bone.masterdata.application.query.dto.QualityResultDTO;
import com.bone.masterdata.application.query.qry.DataQualityRuleDetailQuery;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.masterdata.domain.quality.QualityCheck;
import com.bone.masterdata.domain.quality.QualityReport;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.QualityCheckRepository;
import com.bone.masterdata.domain.repository.QualityReportRepository;
import com.bone.masterdata.domain.service.quality.DataQualityService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据质量应用服务（ADR-0028 Application Service First）。
 *
 * <p>读侧领域模型由各 Repository 的 default 方法承载（ADR-0030），应用层不直接依赖持久化 DSL。
 */
@Service
@RequiredArgsConstructor
public class QualityApplicationService {

  private final DataQualityRuleRepository dataQualityRuleRepository;
  private final MasterDataRecordRepository recordRepository;
  private final MasterDataEntityRepository entityRepository;
  private final QualityCheckRepository qualityCheckRepository;
  private final QualityReportRepository qualityReportRepository;
  private final DataQualityService dataQualityService;
  private final MasterdataDomainEventPublisher domainEventPublisher;

  @Capability(
      name = "CreateDataQualityRule",
      description = "创建数据质量规则",
      inputSchema =
          "{\"masterDataEntityId\": \"long\", \"name\": \"string\", \"type\": \"string\", \"expression\": \"string\", \"severity\": \"string\", \"description\": \"string\", \"enabled\": \"boolean\"}",
      outputSchema = "{\"ruleId\": \"long\"}",
      idempotent = false,
      cost = 2,
      retryable = true,
      timeout = 15)
  @Transactional
  public Long createRule(CreateDataQualityRuleCommand cmd) {
    RuleName name = RuleName.of(cmd.getName());
    long existing = dataQualityRuleRepository.countByName(name);
    if (existing > 0) {
      throw MasterDataErrors.of(MasterDataErrorCodes.RULE_NAME_DUPLICATE, "规则名称已存在");
    }
    Long ruleId = DistributedIdGenerator.generateLongId();
    DataQualityRule rule =
        DataQualityRule.create(
            ruleId,
            cmd.getMasterDataEntityId(),
            name,
            cmd.getType(),
            cmd.getExpression(),
            cmd.getSeverity(),
            cmd.getDescription());
    Long savedId = dataQualityRuleRepository.save(rule);
    domainEventPublisher.publishFrom(rule);
    return savedId;
  }

  @Capability(
      name = "UpdateDataQualityRule",
      description = "更新数据质量规则",
      inputSchema =
          "{\"id\": \"long\", \"name\": \"string\", \"type\": \"string\", \"expression\": \"string\", \"severity\": \"string\", \"description\": \"string\", \"enabled\": \"boolean\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void updateRule(UpdateDataQualityRuleCommand cmd) {
    DataQualityRule rule = dataQualityRuleRepository.findById(cmd.getId());
    if (rule == null) {
      throw NotFoundException.of("数据质量规则不存在");
    }
    if (cmd.getSeverity() == null || cmd.getSeverity().isBlank()) {
      throw new BizException(400, "规则严重级别不能为空");
    }
    rule.update(
        RuleName.of(cmd.getName()),
        cmd.getType(),
        cmd.getExpression(),
        RuleSeverity.valueOf(cmd.getSeverity()),
        cmd.getDescription());
    dataQualityRuleRepository.update(rule);
    domainEventPublisher.publishFrom(rule);
  }

  @Capability(
      name = "DeleteDataQualityRule",
      description = "删除数据质量规则",
      inputSchema = "{\"id\": \"long\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void deleteRule(Long id) {
    DataQualityRule rule = dataQualityRuleRepository.findById(id);
    if (rule == null) {
      throw NotFoundException.of("数据质量规则不存在");
    }
    dataQualityRuleRepository.deleteById(id);
  }

  @Capability(
      name = "PerformDataQualityCheck",
      description = "执行数据质量检查",
      inputSchema = "{\"masterDataEntityId\": \"long\"}",
      outputSchema = "{\"checkId\": \"long\"}",
      idempotent = false,
      cost = 5,
      retryable = true,
      timeout = 120)
  @Transactional(readOnly = true)
  public Long performCheck(PerformDataQualityCheckCommand cmd) {
    if (entityRepository.findById(cmd.getMasterDataEntityId()) == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    // 规则表达式求值器尚未交付：宁可显式 501，也不用随机结果冒充质量结论。
    throw new BizException(501, "MD_QUALITY_CHECK_UNSUPPORTED: 规则表达式求值器未实现");
  }

  @Transactional(readOnly = true)
  public List<DataQualityRuleDTO> ruleList(DataQualityRuleListQuery qry) {
    List<DataQualityRule> rules =
        dataQualityRuleRepository.findByMasterDataEntityId(qry.getMasterDataEntityId());
    if (qry.getType() != null && !qry.getType().isBlank()) {
      rules =
          rules.stream()
              .filter(r -> r.getType() != null && r.getType().equals(qry.getType()))
              .toList();
    }
    if (qry.getSeverity() != null && !qry.getSeverity().isBlank()) {
      rules =
          rules.stream()
              .filter(
                  r -> r.getSeverity() != null && r.getSeverity().name().equals(qry.getSeverity()))
              .toList();
    }
    return rules.stream().map(this::toRuleDto).toList();
  }

  @Transactional(readOnly = true)
  public DataQualityRuleDTO ruleDetail(DataQualityRuleDetailQuery qry) {
    DataQualityRule rule = dataQualityRuleRepository.findById(qry.id());
    if (rule == null) {
      throw NotFoundException.of("数据质量规则不存在");
    }
    return toRuleDto(rule);
  }

  @Transactional(readOnly = true)
  public QualityReportDTO report(Long reportId) {
    QualityReport report = qualityReportRepository.findById(reportId);
    if (report == null) {
      throw NotFoundException.of("质量报告不存在");
    }
    return toReportDto(report);
  }

  private QualityReportDTO toReportDto(QualityReport report) {
    return new QualityReportDTO(
        report.getId(),
        report.getQualityCheckId(),
        report.getReportData(),
        report.getIssueCount(),
        report.getCreatedAt());
  }

  /** 按实体查询质量检查列表（原 DataQualityController.listChecks 内联，读 DSL 下沉到 Repository）。 */
  @Transactional(readOnly = true)
  public List<QualityCheckDTO> listChecks(Long masterDataEntityId) {
    List<QualityCheck> checks =
        masterDataEntityId != null
            ? qualityCheckRepository.findByMasterDataEntityId(masterDataEntityId)
            : qualityCheckRepository.findAllChecks();
    return checks.stream().map(this::toCheckDto).toList();
  }

  /** 按质量检查 ID 查询报告列表。 */
  @Transactional(readOnly = true)
  public List<QualityReportDTO> listReports(Long qualityCheckId) {
    return qualityReportRepository.findByQualityCheckId(qualityCheckId).stream()
        .map(this::toReportDto)
        .toList();
  }

  /** 查询质量结果（原 QualityResultController 内联，读 DSL 下沉到 Repository）。 */
  @Transactional(readOnly = true)
  public List<QualityResultDTO> listQualityResults(Long recordId, Long masterDataEntityId) {
    List<QualityCheck> checks =
        masterDataEntityId != null
            ? qualityCheckRepository.findByMasterDataEntityId(masterDataEntityId)
            : qualityCheckRepository.findAllChecks();
    Map<Long, List<QualityReport>> reportsByCheck =
        qualityReportRepository
            .findByQualityCheckIds(checks.stream().map(QualityCheck::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(QualityReport::getQualityCheckId));

    List<QualityResultDTO> results = new ArrayList<>();
    for (QualityCheck check : checks) {
      List<QualityReport> reports = reportsByCheck.getOrDefault(check.getId(), List.of());
      long failed = check.getFailedRecords() != null ? check.getFailedRecords() : 0L;
      for (QualityReport report : reports) {
        Long ruleId = report.getQualityCheckId();
        results.add(
            QualityResultDTO.builder()
                .id(report.getId())
                .masterDataRecordId(recordId)
                .dataQualityRuleId(ruleId)
                .passed(failed == 0L)
                .message(buildMessage(failed, report))
                .timestamp(
                    report.getCreatedAt() != null ? report.getCreatedAt() : LocalDateTime.now())
                .build());
      }
      if (reports.isEmpty()) {
        results.add(
            QualityResultDTO.builder()
                .id(check.getId())
                .masterDataRecordId(recordId)
                .dataQualityRuleId(check.getMasterDataEntityId())
                .passed(failed == 0L)
                .message(failed == 0L ? "质量检查通过" : "质量检查存在 " + failed + " 条未通过记录")
                .timestamp(check.getEndedAt() != null ? check.getEndedAt() : LocalDateTime.now())
                .build());
      }
    }
    return results;
  }

  private DataQualityRuleDTO toRuleDto(DataQualityRule rule) {
    DataQualityRuleDTO dto = new DataQualityRuleDTO();
    dto.setId(rule.getId());
    dto.setMasterDataEntityId(rule.getMasterDataEntityId());
    dto.setName(rule.getName().value());
    dto.setType(rule.getType());
    dto.setExpression(rule.getExpression());
    dto.setSeverity(rule.getSeverity());
    dto.setDescription(rule.getDescription());
    dto.setCreatedAt(rule.getCreatedAt());
    dto.setUpdatedAt(rule.getUpdatedAt());
    return dto;
  }

  private QualityCheckDTO toCheckDto(QualityCheck c) {
    return QualityCheckDTO.builder()
        .id(c.getId())
        .masterDataEntityId(c.getMasterDataEntityId())
        .status(c.getStatus())
        .totalRecords(c.getTotalRecords())
        .passedRecords(c.getPassedRecords())
        .failedRecords(c.getFailedRecords())
        .startedAt(toDate(c.getStartedAt()))
        .endedAt(toDate(c.getEndedAt()))
        .build();
  }

  private Date toDate(LocalDateTime time) {
    return time != null ? Date.from(time.atZone(ZoneId.systemDefault()).toInstant()) : null;
  }

  private String buildMessage(long failed, QualityReport report) {
    Integer issues = report.getIssueCount();
    if (issues != null && issues > 0) {
      return "发现 " + issues + " 个质量问题";
    }
    return failed > 0 ? "部分记录未通过校验" : "通过";
  }
}
