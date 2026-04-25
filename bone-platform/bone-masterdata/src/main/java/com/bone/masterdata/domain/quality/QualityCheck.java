package com.bone.masterdata.domain.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.masterdata.domain.quality.event.QualityCheckCompletedEvent;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("md_quality_check")
public class QualityCheck extends AggregateRoot<Long> {
    private Long id;
    private Long masterDataEntityId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer totalRecords;
    private Integer passedRecords;
    private Integer failedRecords;
    private LocalDateTime createTime;

    public static QualityCheck create(Long id, Long masterDataEntityId) {
        QualityCheck check = new QualityCheck();
        check.id = id;
        check.masterDataEntityId = masterDataEntityId;
        check.startTime = LocalDateTime.now();
        check.status = "RUNNING";
        check.createTime = LocalDateTime.now();
        return check;
    }

    public void complete(Integer totalRecords, Integer passedRecords, Integer failedRecords) {
        this.endTime = LocalDateTime.now();
        this.status = "COMPLETED";
        this.totalRecords = totalRecords;
        this.passedRecords = passedRecords;
        this.failedRecords = failedRecords;
        this.addDomainEvent(new QualityCheckCompletedEvent(this));
    }
}
