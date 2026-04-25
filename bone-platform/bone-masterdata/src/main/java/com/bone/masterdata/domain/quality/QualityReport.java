package com.bone.masterdata.domain.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("md_quality_report")
public class QualityReport extends AggregateRoot<Long> {
    private Long id;
    private Long qualityCheckId;
    private String reportData;
    private Integer issueCount;
    private LocalDateTime createTime;

    public static QualityReport create(Long id, Long qualityCheckId, String reportData, Integer issueCount) {
        QualityReport report = new QualityReport();
        report.id = id;
        report.qualityCheckId = qualityCheckId;
        report.reportData = reportData;
        report.issueCount = issueCount;
        report.createTime = LocalDateTime.now();
        return report;
    }
}
