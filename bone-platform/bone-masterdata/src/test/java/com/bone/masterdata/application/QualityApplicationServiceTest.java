package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.masterdata.application.command.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.PerformDataQualityCheckCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.QualityCheck;
import com.bone.masterdata.domain.model.quality.QualityReport;
import com.bone.masterdata.domain.model.quality.valueobject.RuleName;
import com.bone.masterdata.domain.model.quality.valueobject.RuleSeverity;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
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

    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(1L);
    service.performCheck(cmd);

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
}
