package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.masterdata.application.command.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.PerformDataQualityCheckCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.application.query.dto.QualityResultDTO;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.QualityCheck;
import com.bone.masterdata.domain.model.quality.QualityCheckDetail;
import com.bone.masterdata.domain.model.quality.QualityReport;
import com.bone.masterdata.domain.model.quality.valueobject.RuleName;
import com.bone.masterdata.domain.model.quality.valueobject.RuleSeverity;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.QualityCheckDetailRepository;
import com.bone.masterdata.domain.repository.QualityCheckRepository;
import com.bone.masterdata.domain.repository.QualityReportRepository;
import com.bone.masterdata.domain.service.quality.DataQualityService;
import com.bone.masterdata.domain.service.quality.RuleExpressionEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QualityApplicationServiceTest {

  @Mock private DataQualityRuleRepository dataQualityRuleRepository;
  @Mock private MasterDataRecordRepository recordRepository;
  @Mock private MasterDataEntityRepository entityRepository;
  @Mock private QualityCheckRepository qualityCheckRepository;
  @Mock private QualityReportRepository qualityReportRepository;
  @Mock private QualityCheckDetailRepository qualityCheckDetailRepository;
  @Mock private DataQualityService dataQualityService;
  @Mock private MasterdataDomainEventPublisher domainEventPublisher;

  private QualityApplicationService service;

  /**
   * 求值器与 ObjectMapper 是有真实行为的无状态协作对象，不能用 @Spy（final class 无法 spy）—— 直接构造器注入，避免为可测性把领域服务改成非 final。
   */
  @BeforeEach
  void setUp() {
    service =
        new QualityApplicationService(
            dataQualityRuleRepository,
            recordRepository,
            entityRepository,
            qualityCheckRepository,
            qualityReportRepository,
            qualityCheckDetailRepository,
            dataQualityService,
            domainEventPublisher,
            new RuleExpressionEvaluator(),
            new ObjectMapper());
  }

  private static DataQualityRule rule(String type, String expression) {
    return DataQualityRule.create(
        1L, 1L, RuleName.of("编码非空"), type, expression, RuleSeverity.HIGH, null);
  }

  /** 写路径必须发布领域事件——ADR-0032 收敛时漏过这一步，曾导致 6 个 Handler 全部静默失效。 */
  @Test
  void createRulePublishesDomainEvent() {
    when(dataQualityRuleRepository.countByName(any())).thenReturn(0L);
    when(dataQualityRuleRepository.save(any())).thenReturn(11L);

    CreateDataQualityRuleCommand cmd = new CreateDataQualityRuleCommand();
    cmd.setMasterDataEntityId(1L);
    cmd.setName("非空校验");
    cmd.setType("NOT_NULL");
    cmd.setExpression("field=code");
    cmd.setSeverity(RuleSeverity.HIGH);

    assertEquals(11L, service.createRule(cmd));
    verify(domainEventPublisher).publishFrom(any(DataQualityRule.class));
  }

  /** 表达式必须可被求值才允许入库：否则"创建成功、检查时才说不认识"。 */
  @Test
  void createRuleRejectsExpressionThatCannotBeEvaluated() {
    when(dataQualityRuleRepository.countByName(any())).thenReturn(0L);

    CreateDataQualityRuleCommand cmd = new CreateDataQualityRuleCommand();
    cmd.setMasterDataEntityId(1L);
    cmd.setName("格式校验");
    cmd.setType("FORMAT");
    cmd.setExpression("zip 必须为 6 位数字");
    cmd.setSeverity(RuleSeverity.HIGH);

    BizException ex = assertThrows(BizException.class, () -> service.createRule(cmd));
    assertEquals(400, ex.getCode());
  }

  /** 质量检查真实求值：统计记录通过/失败数，落检查与报告，并发布完成事件。 */
  @Test
  void performCheckEvaluatesRulesAndPersistsReport() {
    when(entityRepository.findById(1L)).thenReturn(mock(MasterDataEntity.class));
    when(dataQualityRuleRepository.findByMasterDataEntityId(1L))
        .thenReturn(List.of(rule("NOT_NULL", "field=code")));
    when(recordRepository.findByMasterDataEntityId(1L))
        .thenReturn(
            List.of(
                MasterDataRecord.create(101L, 1L, "{\"code\":\"A\"}"),
                MasterDataRecord.create(102L, 1L, "{\"code\":\"\"}")));
    when(qualityCheckRepository.save(any(QualityCheck.class))).thenReturn(1L);

    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(1L);
    service.performCheck(cmd);

    // 生命周期 RUNNING → COMPLETED：先 save 落 RUNNING，求值后 update 写结果。
    // SDK 已根治租户缺口（tenant_id 不再进 UPDATE SET），两步写不再 500。
    verify(qualityCheckRepository).save(any(QualityCheck.class));
    ArgumentCaptor<QualityCheck> checkCaptor = ArgumentCaptor.forClass(QualityCheck.class);
    verify(qualityCheckRepository).update(checkCaptor.capture());
    QualityCheck check = checkCaptor.getValue();
    assertEquals("COMPLETED", check.getStatus());
    assertEquals(2, check.getTotalRecords());
    assertEquals(1, check.getPassedRecords());
    assertEquals(1, check.getFailedRecords());

    ArgumentCaptor<QualityReport> reportCaptor = ArgumentCaptor.forClass(QualityReport.class);
    verify(qualityReportRepository).save(reportCaptor.capture());
    assertEquals(1, reportCaptor.getValue().getIssueCount());
    assertTrue(reportCaptor.getValue().getReportData().contains("\"ruleName\":\"编码非空\""));

    verify(domainEventPublisher).publishFrom(any(QualityCheck.class));
  }

  /** 不受支持的规则类型在报告中显式标注为未求值，不得计入"通过"。 */
  @Test
  void performCheckReportsUnsupportedRuleExplicitly() {
    when(entityRepository.findById(1L)).thenReturn(mock(MasterDataEntity.class));
    when(dataQualityRuleRepository.findByMasterDataEntityId(1L))
        .thenReturn(List.of(rule("CUSTOM", "amount > 0")));
    when(recordRepository.findByMasterDataEntityId(1L))
        .thenReturn(List.of(MasterDataRecord.create(101L, 1L, "{\"code\":\"A\"}")));

    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(1L);
    service.performCheck(cmd);

    ArgumentCaptor<QualityReport> reportCaptor = ArgumentCaptor.forClass(QualityReport.class);
    verify(qualityReportRepository).save(reportCaptor.capture());
    String reportData = reportCaptor.getValue().getReportData();
    assertTrue(reportData.contains("unsupportedRules"));
    assertEquals(0, reportCaptor.getValue().getIssueCount());
  }

  /** 实体不存在时保持 404，不因检查能力上线而改变。 */
  @Test
  void performCheckRequiresExistingEntity() {
    when(entityRepository.findById(1L)).thenReturn(null);

    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(1L);

    assertThrows(BizException.class, () -> service.performCheck(cmd));
  }

  /**
   * 每次检查必须落「规则 × 记录」明细。
   *
   * <p>{@code mdm_qcheck_detail} 此前只有 DDL 没有写入链路，是"按记录查质量结果"无法实现的根因；这里守住写路径，防止有人只留报告 JSON 又把明细删回去。
   */
  @Test
  void performCheckPersistsRuleRecordDetails() {
    when(entityRepository.findById(1L)).thenReturn(mock(MasterDataEntity.class));
    when(dataQualityRuleRepository.findByMasterDataEntityId(1L))
        .thenReturn(List.of(rule("NOT_NULL", "field=code")));
    when(recordRepository.findByMasterDataEntityId(1L))
        .thenReturn(
            List.of(
                MasterDataRecord.create(101L, 1L, "C-001", "客户一", "{\"code\":\"A\"}"),
                MasterDataRecord.create(102L, 1L, "C-002", "客户二", "{\"code\":\"\"}")));

    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(1L);
    service.performCheck(cmd);

    // check.getId() 是真实生成的雪花 ID，mock save() 的返回值不会回写实体，所以要比对必须取实体本身。
    ArgumentCaptor<QualityCheck> checkCaptor = ArgumentCaptor.forClass(QualityCheck.class);
    verify(qualityCheckRepository).save(checkCaptor.capture());
    Long checkId = checkCaptor.getValue().getId();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<QualityCheckDetail>> captor = ArgumentCaptor.forClass(List.class);
    verify(qualityCheckDetailRepository).batchInsert(captor.capture());
    List<QualityCheckDetail> details = captor.getValue();
    assertEquals(2, details.size());
    // 违规行必须为 failed 且带上业务编码，能直接定位到是哪条记录
    QualityCheckDetail failed =
        details.stream().filter(d -> !Boolean.TRUE.equals(d.getPassed())).findFirst().orElseThrow();
    assertEquals(102L, failed.getRecordId());
    assertEquals(checkId, failed.getQualityCheckId());
    assertTrue(failed.getMessage().contains("C-002"), "明细文案应含业务编码：" + failed.getMessage());
    assertEquals(1, details.stream().filter(d -> Boolean.TRUE.equals(d.getPassed())).count());
  }

  /** 未求值规则不得落 passed=true 明细：把"没算过"粉饰成"没问题"是质量治理里最危险的假阳性。 */
  @Test
  void performCheckSkipsDetailsForUnsupportedRule() {
    when(entityRepository.findById(1L)).thenReturn(mock(MasterDataEntity.class));
    when(dataQualityRuleRepository.findByMasterDataEntityId(1L))
        .thenReturn(List.of(rule("CUSTOM", "amount > 0")));
    when(recordRepository.findByMasterDataEntityId(1L))
        .thenReturn(List.of(MasterDataRecord.create(101L, 1L, "C-001", "客户一", "{\"code\":\"A\"}")));

    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(1L);
    service.performCheck(cmd);

    verify(qualityCheckDetailRepository, never()).batchInsert(any());
  }

  /**
   * 传 recordId 时必须走明细表真筛选。
   *
   * <p>旧实现把 recordId 原样回填 DTO 而完全不参与筛选，返回结果与该记录无关——这正是"按记录查质量结果"语义不成立的证据。
   */
  @Test
  void listQualityResultsFiltersDetailsByRecordId() {
    when(qualityCheckRepository.findAllChecks()).thenReturn(List.of(QualityCheck.create(7L, 1L)));
    when(qualityCheckDetailRepository.findByRecordId(102L))
        .thenReturn(
            List.of(
                QualityCheckDetail.fail(900L, 7L, 1L, 102L, "非空校验(NOT_NULL) → 客户二：字段[code]为必填项"),
                QualityCheckDetail.pass(901L, 7L, 2L, 102L, "格式校验(FORMAT) → 客户二：通过")));

    List<QualityResultDTO> results = service.listQualityResults(102L, null);

    assertEquals(2, results.size());
    for (QualityResultDTO dto : results) {
      assertEquals("DETAIL", dto.getLevel());
      assertEquals(102L, dto.getMasterDataRecordId());
      assertEquals(7L, dto.getQualityCheckId());
    }
    assertTrue(results.get(0).getMessage().contains("客户二"));
  }

  /** 汇总行不得把 checkId 冒充成"规则 ID"——旧实现正是这么干的，前端规则列显示的既不是规则也不是记录。 */
  @Test
  void listQualityResultsSummaryRowHasNoRuleId() {
    QualityCheck check = QualityCheck.create(7L, 1L);
    check.complete(10, 8, 2);
    when(qualityCheckRepository.findByMasterDataEntityId(1L)).thenReturn(List.of(check));
    when(qualityReportRepository.findByQualityCheckIds(List.of(7L)))
        .thenReturn(List.of(QualityReport.create(50L, 7L, "{}", 3)));

    List<QualityResultDTO> results = service.listQualityResults(null, 1L);

    assertEquals(1, results.size());
    QualityResultDTO dto = results.get(0);
    assertEquals("SUMMARY", dto.getLevel());
    assertEquals(7L, dto.getQualityCheckId());
    assertEquals(1L, dto.getMasterDataEntityId());
    org.junit.jupiter.api.Assertions.assertNull(dto.getDataQualityRuleId());
    org.junit.jupiter.api.Assertions.assertNull(dto.getMasterDataRecordId());
    assertEquals("发现 3 个质量问题", dto.getMessage());
  }
}
