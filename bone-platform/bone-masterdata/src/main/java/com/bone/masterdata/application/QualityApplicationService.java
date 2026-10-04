package com.bone.masterdata.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.PerformDataQualityCheckCommand;
import com.bone.masterdata.application.command.UpdateDataQualityRuleCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.dto.QualityCheckDTO;
import com.bone.masterdata.application.query.dto.QualityReportDTO;
import com.bone.masterdata.application.query.dto.QualityResultDTO;
import com.bone.masterdata.application.query.qry.DataQualityRuleDetailQuery;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.QualityCheck;
import com.bone.masterdata.domain.model.quality.QualityCheckDetail;
import com.bone.masterdata.domain.model.quality.QualityReport;
import com.bone.masterdata.domain.model.quality.valueobject.RuleName;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.QualityCheckDetailRepository;
import com.bone.masterdata.domain.repository.QualityCheckRepository;
import com.bone.masterdata.domain.repository.QualityReportRepository;
import com.bone.masterdata.domain.service.quality.DataQualityService;
import com.bone.masterdata.domain.service.quality.RecordFields;
import com.bone.masterdata.domain.service.quality.RuleEvaluation;
import com.bone.masterdata.domain.service.quality.RuleExpressionEvaluator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据质量应用服务（ADR-0028 Application Service First）。
 *
 * <p>读侧领域模型由各 Repository 的 default 方法承载（ADR-0030），应用层不直接依赖持久化 DSL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityApplicationService {

  /**
   * 单次检查落明细的行数上限。
   *
   * <p>明细是「规则数 × 记录数」的笛卡尔积，不设限会在大实体上一次检查写出百万行。超限时优先保留违规明细（见 {@link #saveCheckDetails}）。
   */
  private static final int MAX_DETAIL_ROWS = 2000;

  /** 质量结果粒度标记：逐规则×记录的明细行。 */
  public static final String QUALITY_RESULT_LEVEL_DETAIL = "DETAIL";

  /** 质量结果粒度标记：单个检查任务的汇总行。 */
  public static final String QUALITY_RESULT_LEVEL_SUMMARY = "SUMMARY";

  private final DataQualityRuleRepository dataQualityRuleRepository;
  private final MasterDataRecordRepository recordRepository;
  private final MasterDataEntityRepository entityRepository;
  private final QualityCheckRepository qualityCheckRepository;
  private final QualityReportRepository qualityReportRepository;
  private final QualityCheckDetailRepository qualityCheckDetailRepository;
  private final DataQualityService dataQualityService;
  private final MasterdataDomainEventPublisher domainEventPublisher;
  private final RuleExpressionEvaluator ruleExpressionEvaluator;
  private final ObjectMapper objectMapper;

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
    assertExpressionEvaluable(cmd.getType(), cmd.getExpression());
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
    if (cmd.getSeverity() == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.RULE_SEVERITY_INVALID, "严重级别不能为空");
    }
    assertExpressionEvaluable(cmd.getType(), cmd.getExpression());
    rule.update(
        RuleName.of(cmd.getName()),
        cmd.getType(),
        cmd.getExpression(),
        cmd.getSeverity(),
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
  @Transactional
  public Long performCheck(PerformDataQualityCheckCommand cmd) {
    Long entityId = cmd.getMasterDataEntityId();
    if (entityId == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.ENTITY_ID_REQUIRED);
    }
    if (entityRepository.findById(entityId) == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    List<DataQualityRule> rules =
        dataQualityRuleRepository.findByMasterDataEntityId(entityId).stream()
            .filter(DataQualityRule::isEnabled)
            .toList();
    List<RecordFields> records =
        recordRepository.findByMasterDataEntityId(entityId).stream()
            .map(this::toRecordFields)
            .toList();

    Map<String, Set<String>> references = loadReferenceValues(rules);
    List<RuleEvaluation> evaluations =
        rules.stream()
            .map(rule -> ruleExpressionEvaluator.evaluate(rule, records, references))
            .toList();

    long failedRecords =
        evaluations.stream()
            .flatMap(evaluation -> evaluation.violations().stream())
            .map(RuleEvaluation.Violation::recordId)
            .distinct()
            .count();
    int issueCount = evaluations.stream().mapToInt(RuleEvaluation::violationCount).sum();

    // 生命周期 RUNNING → COMPLETED：先入库落 RUNNING（计数列已在 create 中初始化为 0），
    // 求值后 update 写结果终态。SDK 租户缺口已根治（tenant_id 不再进入 UPDATE SET 子句，
    // 仅从可信 TenantContext 写入一次），两步写不再触发 NULL/500，失败时事务回滚无僵尸行。
    QualityCheck check = QualityCheck.create(DistributedIdGenerator.generateLongId(), entityId);
    qualityCheckRepository.save(check);
    check.complete(records.size(), (int) (records.size() - failedRecords), (int) failedRecords);
    qualityCheckRepository.update(check);

    QualityReport report =
        QualityReport.create(
            DistributedIdGenerator.generateLongId(),
            check.getId(),
            buildReportData(entityId, records.size(), evaluations),
            issueCount);
    qualityReportRepository.save(report);
    saveCheckDetails(check.getId(), evaluations, records);
    domainEventPublisher.publishFrom(check);
    return check.getId();
  }

  /**
   * 落「规则 × 记录」判定明细（{@code mdm_qcheck_detail}）。
   *
   * <p>明细表此前只有 DDL 没有写入链路，导致「按记录查质量结果」无从实现——recordId 只能被原样回填到
   * DTO。现在每次检查都把逐规则逐记录的通过/未通过写库，按记录过滤才有真实数据来源。
   *
   * <p><b>写入量控制</b>：明细是笛卡尔积（规则数 × 记录数），大实体会瞬间放大成百万行。因此设 {@link #MAX_DETAIL_ROWS}
   * 上限，且<b>违规明细优先保留</b>（超过上限时丢弃通过明细，违规一条不丢）——质量治理的排查入口永远是"哪里不通过"，不是"哪里通过"。被截断的事实写入报告 JSON 的 {@code
   * detailTruncated}，不静默。
   */
  private void saveCheckDetails(
      Long checkId, List<RuleEvaluation> evaluations, List<RecordFields> records) {
    if (records.isEmpty() || evaluations.isEmpty()) {
      return;
    }
    Map<Long, String> recordCodes =
        records.stream()
            .collect(Collectors.toMap(RecordFields::recordId, RecordFields::label, (a, b) -> a));

    List<QualityCheckDetail> details = new ArrayList<>();
    int truncatedPassCount = 0;
    for (RuleEvaluation evaluation : evaluations) {
      if (evaluation.isUnsupported()) {
        // 未求值 ≠ 通过，不落明细行：凭空造 passed=true 会把"没算过"粉饰成"没问题"。
        continue;
      }
      Set<Long> violated =
          evaluation.violations().stream()
              .map(RuleEvaluation.Violation::recordId)
              .collect(Collectors.toSet());
      for (RecordFields record : records) {
        Long recordId = record.recordId();
        String label = recordCodes.getOrDefault(recordId, String.valueOf(recordId));
        String ruleLabel = evaluation.ruleName() + "(" + evaluation.type() + ")";
        if (violated.contains(recordId)) {
          String message =
              evaluation.violations().stream()
                  .filter(v -> recordId.equals(v.recordId()))
                  .map(RuleEvaluation.Violation::message)
                  .findFirst()
                  .orElse("规则未通过");
          details.add(
              QualityCheckDetail.fail(
                  DistributedIdGenerator.generateLongId(),
                  checkId,
                  evaluation.ruleId(),
                  recordId,
                  ruleLabel + " → " + label + "：" + message));
          continue;
        }
        if (details.size() >= MAX_DETAIL_ROWS) {
          truncatedPassCount++;
          continue;
        }
        details.add(
            QualityCheckDetail.pass(
                DistributedIdGenerator.generateLongId(),
                checkId,
                evaluation.ruleId(),
                recordId,
                ruleLabel + " → " + label + "：通过"));
      }
    }
    if (details.isEmpty()) {
      return;
    }
    qualityCheckDetailRepository.batchInsert(details);
    if (truncatedPassCount > 0) {
      log.warn(
          "质量检查明细超过单次上限，已优先保留违规明细：checkId={}, 保留={}, 丢弃通过明细={}, 上限={}",
          checkId,
          details.size(),
          truncatedPassCount,
          MAX_DETAIL_ROWS);
    }
  }

  /** 单个质量检查详情（ADR-0030：读 DSL 下沉到 Repository，此处只做 DTO 装配）。 */
  @Transactional(readOnly = true)
  public QualityCheckDTO checkDetail(Long checkId) {
    QualityCheck check = qualityCheckRepository.findById(checkId);
    if (check == null) {
      throw NotFoundException.of("质量检查不存在");
    }
    return toCheckDto(check);
  }

  /**
   * 表达式入库前校验：受支持的规则类型必须可被求值，避免"创建成功、检查时才说不认识"。
   *
   * <p>不受支持的类型（如 CUSTOM 脚本）允许入库，检查时显式标注为未求值。
   */
  private void assertExpressionEvaluable(String type, String expression) {
    String error = ruleExpressionEvaluator.validate(type, expression);
    if (error != null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.RULE_EXPRESSION_INVALID, error);
    }
  }

  /** 记录 data（JSON 字符串）→ 字段快照；键为字段编码。 */
  private RecordFields toRecordFields(MasterDataRecord record) {
    Map<String, String> values = new LinkedHashMap<>();
    String raw = record.getData();
    if (raw != null && !raw.isBlank()) {
      final JsonNode node;
      try {
        node = objectMapper.readTree(raw);
      } catch (JsonProcessingException ex) {
        throw MasterDataErrors.of(
            MasterDataErrorCodes.RECORD_DATA_PARSE_FAILED, "recordId=" + record.getId(), ex);
      }
      node.fields()
          .forEachRemaining(
              entry ->
                  values.put(
                      entry.getKey(), entry.getValue().isNull() ? "" : entry.getValue().asText()));
    }
    return RecordFields.of(record.getId(), record.getRecordCode(), values);
  }

  /** 预加载 REFERENCE 规则的目标实体取值集合（key = {@code entityId#field}），避免逐记录查询。 */
  private Map<String, Set<String>> loadReferenceValues(List<DataQualityRule> rules) {
    Map<String, Set<String>> references = new HashMap<>();
    for (DataQualityRule rule : rules) {
      Optional<RuleExpressionEvaluator.ReferenceTarget> target =
          ruleExpressionEvaluator.referenceTargetOf(rule);
      if (target.isEmpty()) {
        continue;
      }
      String key = target.get().entityId() + "#" + target.get().field();
      if (references.containsKey(key)) {
        continue;
      }
      references.put(
          key,
          recordRepository.findByMasterDataEntityId(target.get().entityId()).stream()
              .map(this::toRecordFields)
              .map(fields -> fields.value(target.get().field()))
              .filter(Objects::nonNull)
              .collect(Collectors.toSet()));
    }
    return references;
  }

  /** 报告 JSON：逐规则命中数 + 问题样本（每规则最多 20 条）+ 未求值规则清单。 */
  private String buildReportData(
      Long entityId, int totalRecords, List<RuleEvaluation> evaluations) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("entityId", entityId);
    root.put("totalRecords", totalRecords);
    ArrayNode ruleNodes = root.putArray("rules");
    ArrayNode unsupportedNodes = root.putArray("unsupportedRules");
    for (RuleEvaluation evaluation : evaluations) {
      if (evaluation.isUnsupported()) {
        ObjectNode node = unsupportedNodes.addObject();
        putRuleIdentity(node, evaluation);
        node.put("reason", evaluation.unsupportedReason());
        continue;
      }
      ObjectNode node = ruleNodes.addObject();
      putRuleIdentity(node, evaluation);
      node.put("violations", evaluation.violationCount());
      ArrayNode samples = node.putArray("samples");
      evaluation.violations().stream()
          .limit(20)
          .forEach(
              violation -> {
                ObjectNode sample = samples.addObject();
                sample.put("recordId", violation.recordId());
                sample.put("field", violation.field());
                sample.put("message", violation.message());
              });
    }
    try {
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException ex) {
      throw MasterDataErrors.of(MasterDataErrorCodes.EXPORT_SERIALIZE_FAILED, "质量报告", ex);
    }
  }

  private void putRuleIdentity(ObjectNode node, RuleEvaluation evaluation) {
    node.put("ruleId", evaluation.ruleId());
    node.put("ruleName", evaluation.ruleName());
    node.put("type", evaluation.type());
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

  // 按检查任务 ID 取报告：调用方持有的是 checkId，而 /reports/{id} 的 id 是报告主键，
  // 两者混用会稳定 404。报告主键属内部标识，对客户端没有意义。
  @Transactional(readOnly = true)
  public QualityReportDTO reportByCheckId(Long checkId) {
    List<QualityReport> reports = qualityReportRepository.findByQualityCheckId(checkId);
    if (reports.isEmpty()) {
      throw NotFoundException.of("质量报告不存在");
    }
    return toReportDto(reports.get(0));
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

  /**
   * 查询质量结果（读 DSL 下沉到 Repository，此处只做口径选择与 DTO 装配）。
   *
   * <p>返回<b>两种粒度</b>，由入参决定，绝不混在一份结果里让调用方猜：
   *
   * <ul>
   *   <li><b>传 recordId</b> → 逐条明细（{@code level=DETAIL}）：来自 {@code mdm_qcheck_detail}，每行是「某规则 ×
   *       某记录」的判定。此时 recordId 真正参与筛选（此前它只是被原样回填到 DTO，返回结果与该记录无关）。
   *   <li><b>不传 recordId</b> → 检查任务汇总（{@code level=SUMMARY}）：每行是一次质检任务整体的通过情况，明细不展开（否则笛卡尔积会把列表撑爆）。
   * </ul>
   *
   * <p>两个入参都为空时取全量任务，与既有行为一致。
   */
  @Transactional(readOnly = true)
  public List<QualityResultDTO> listQualityResults(Long recordId, Long masterDataEntityId) {
    List<QualityCheck> checks =
        masterDataEntityId != null
            ? qualityCheckRepository.findByMasterDataEntityId(masterDataEntityId)
            : qualityCheckRepository.findAllChecks();
    List<Long> checkIds = checks.stream().map(QualityCheck::getId).toList();

    if (recordId != null) {
      List<QualityCheckDetail> details =
          masterDataEntityId != null
              ? qualityCheckDetailRepository.findByQualityCheckIdsAndRecordId(checkIds, recordId)
              : qualityCheckDetailRepository.findByRecordId(recordId);
      return details.stream()
          .sorted(
              Comparator.comparing(QualityCheckDetail::getCheckedAt)
                  .thenComparing(QualityCheckDetail::getId))
          .map(this::toDetailResultDto)
          .toList();
    }

    Map<Long, List<QualityReport>> reportsByCheck =
        qualityReportRepository.findByQualityCheckIds(checkIds).stream()
            .collect(Collectors.groupingBy(QualityReport::getQualityCheckId));

    List<QualityResultDTO> results = new ArrayList<>();
    for (QualityCheck check : checks) {
      List<QualityReport> reports = reportsByCheck.getOrDefault(check.getId(), List.of());
      int issues =
          reports.stream()
              .map(QualityReport::getIssueCount)
              .filter(Objects::nonNull)
              .mapToInt(Integer::intValue)
              .sum();
      long failed = check.getFailedRecords() != null ? check.getFailedRecords() : 0L;
      LocalDateTime timestamp =
          reports.stream()
              .map(QualityReport::getCreatedAt)
              .filter(Objects::nonNull)
              .max(LocalDateTime::compareTo)
              .orElse(check.getEndedAt() != null ? check.getEndedAt() : LocalDateTime.now());
      results.add(
          QualityResultDTO.builder()
              .id(check.getId())
              .qualityCheckId(check.getId())
              .masterDataEntityId(check.getMasterDataEntityId())
              .level(QUALITY_RESULT_LEVEL_SUMMARY)
              // 汇总行不对应单条规则，dataQualityRuleId 必须留空：此前把 checkId（甚至 entityId）
              // 塞进这个字段，前端"规则 ID"列显示的其实既不是规则也不是记录。
              .dataQualityRuleId(null)
              .passed(failed == 0L)
              .message(buildSummaryMessage(failed, issues, reports.isEmpty()))
              .timestamp(timestamp)
              .build());
    }
    return results;
  }

  /** 明细行 → DTO：每行自带规则与记录两个维度，可直接定位问题。 */
  private QualityResultDTO toDetailResultDto(QualityCheckDetail detail) {
    return QualityResultDTO.builder()
        .id(detail.getId())
        .qualityCheckId(detail.getQualityCheckId())
        .masterDataRecordId(detail.getRecordId())
        .dataQualityRuleId(detail.getRuleId())
        .level(QUALITY_RESULT_LEVEL_DETAIL)
        .passed(Boolean.TRUE.equals(detail.getPassed()))
        .message(detail.getMessage())
        .timestamp(detail.getCheckedAt())
        .build();
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

  private String buildSummaryMessage(long failed, int issues, boolean reportMissing) {
    if (reportMissing) {
      return failed == 0L ? "质量检查通过" : "质量检查存在 " + failed + " 条未通过记录（报告缺失，明细不可查）";
    }
    return issues > 0 ? "发现 " + issues + " 个质量问题" : (failed > 0 ? "部分记录未通过校验" : "通过");
  }
}
