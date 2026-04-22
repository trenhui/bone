package com.bone.masterdata.domain.model.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.masterdata.domain.model.quality.vo.QualityCheckId;
import com.bone.masterdata.domain.model.quality.vo.QualityReportId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QualityReport extends AggregateRoot<QualityReportId> {
    private QualityReportId id;
    private QualityCheckId qualityCheckId;
    private String reportData;
    private Integer issueCount;
    private LocalDateTime createTime;

    public static QualityReport create(QualityCheckId qualityCheckId, String reportData, Integer issueCount) {
        QualityReport report = new QualityReport();
        report.qualityCheckId = qualityCheckId;
        report.reportData = reportData;
        report.issueCount = issueCount;
        report.createTime = LocalDateTime.now();
        return report;
    }

    // 仅供 SDK 回填 ID 使用
    @Override
    public void setId(QualityReportId id) { this.id = id; }
}