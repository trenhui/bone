package com.bone.masterdata.application;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.CreateQualityIssueCommand;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.model.qualityissue.QualityIssue;
import com.bone.masterdata.domain.repository.QualityIssueRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 质量整改工单应用服务（G11，UC-T9）：质检失败 → 工单 → 整改 → 复检关闭。 */
@Service
@RequiredArgsConstructor
public class QualityIssueApplicationService {

  private final QualityIssueRepository issueRepository;
  private final CurrentUserPort currentUserPort;

  @Transactional
  public Long create(CreateQualityIssueCommand cmd) {
    LocalDateTime dueAt =
        cmd.getDueAt() == null || cmd.getDueAt().isBlank()
            ? null
            : LocalDateTime.parse(cmd.getDueAt());
    return issueRepository.insert(
        QualityIssue.open(
            DistributedIdGenerator.generateLongId(),
            cmd.getMasterDataEntityId(),
            cmd.getRecordId(),
            cmd.getCheckId(),
            cmd.getRuleId(),
            cmd.getIssueDesc(),
            cmd.getSeverity(),
            cmd.getAssigneeId(),
            dueAt));
  }

  @Transactional
  public void fix(Long id) {
    QualityIssue issue = requireIssue(id);
    issue.fix(currentUserPort.requireUserId());
    issueRepository.update(issue);
  }

  @Transactional
  public void close(Long id) {
    QualityIssue issue = requireIssue(id);
    issue.close();
    issueRepository.update(issue);
  }

  @Transactional
  public void ignore(Long id) {
    QualityIssue issue = requireIssue(id);
    issue.ignore(currentUserPort.requireUserId());
    issueRepository.update(issue);
  }

  @Transactional(readOnly = true)
  public List<QualityIssue> byEntity(Long masterDataEntityId, String status) {
    return issueRepository.findByEntityIdAndStatus(masterDataEntityId, status);
  }

  /** 实体未关闭工单数（发布软门禁数据源）。 */
  @Transactional(readOnly = true)
  public long openCount(Long masterDataEntityId) {
    return issueRepository.countOpenByEntityId(masterDataEntityId);
  }

  private QualityIssue requireIssue(Long id) {
    QualityIssue issue = issueRepository.findById(id);
    if (issue == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.QUALITY_ISSUE_NOT_FOUND, "工单不存在: " + id);
    }
    return issue;
  }
}
