package com.bone.masterdata.domain.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_qcheck_report")
public class QualityReport extends AggregateRoot<Long> {
    private Long id;

    @Column(name = "check_id")
    private Long qualityCheckId;

    @Column(name = "report_data")
    private String reportData;

    @Column(name = "issue_count")
    private Integer issueCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static QualityReport create(Long id, Long qualityCheckId, String reportData, Integer issueCount) {
        QualityReport report = new QualityReport();
        report.id = id;
        report.qualityCheckId = qualityCheckId;
        report.reportData = reportData;
        report.issueCount = issueCount;
        report.createdAt = LocalDateTime.now();
        return report;
    }
}
