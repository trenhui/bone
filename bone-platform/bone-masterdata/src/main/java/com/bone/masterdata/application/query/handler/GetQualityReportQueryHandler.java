package com.bone.masterdata.application.query.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.application.query.dto.QualityReportDTO;
import com.bone.masterdata.domain.quality.QualityReport;
import com.bone.masterdata.domain.repository.QualityReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetQualityReportQueryHandler {

  private final QualityReportRepository qualityReportRepository;

  @Transactional(readOnly = true)
  public QualityReportDTO handle(Long reportId) {
    QualityReport report = qualityReportRepository.findById(reportId);
    if (report == null) {
      throw NotFoundException.of("质量报告不存在: " + reportId);
    }
    return new QualityReportDTO(
        report.getId(),
        report.getQualityCheckId(),
        report.getReportData(),
        report.getIssueCount(),
        report.getCreatedAt());
  }
}
