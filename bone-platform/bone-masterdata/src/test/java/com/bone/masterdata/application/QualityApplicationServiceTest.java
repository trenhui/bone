package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.bone.masterdata.domain.model.quality.valueobject.RuleSeverity;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.QualityCheckRepository;
import com.bone.masterdata.domain.repository.QualityReportRepository;
import com.bone.masterdata.domain.service.quality.DataQualityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

  @InjectMocks private QualityApplicationService service;

  /** 写路径必须发布领域事件——ADR-0032 收敛时漏过这一步，曾导致 6 个 Handler 全部静默失效。 */
  @Test
  void createRulePublishesDomainEvent() {
    when(dataQualityRuleRepository.countByName(any())).thenReturn(0L);
    when(dataQualityRuleRepository.save(any())).thenReturn(11L);

    CreateDataQualityRuleCommand cmd = new CreateDataQualityRuleCommand();
    cmd.setMasterDataEntityId(1L);
    cmd.setName("非空校验");
    cmd.setType("NOT_NULL");
    cmd.setExpression("data != null");
    cmd.setSeverity(RuleSeverity.HIGH);

    assertEquals(11L, service.createRule(cmd));
    verify(domainEventPublisher).publishFrom(any(DataQualityRule.class));
  }

  /** 规则表达式求值器未交付：必须显式 501，不能产出随机/近似结论。 */
  @Test
  void performCheckIsExplicitlyNotImplemented() {
    when(entityRepository.findById(1L)).thenReturn(mock(MasterDataEntity.class));

    PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
    cmd.setMasterDataEntityId(1L);

    BizException ex = assertThrows(BizException.class, () -> service.performCheck(cmd));
    assertEquals(501, ex.getCode());
  }
}
