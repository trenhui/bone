package com.bone.masterdata.domain.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.masterdata.domain.model.quality.event.QualityCheckCompletedEvent;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_qcheck_task")
public class QualityCheck extends AggregateRoot<Long> {
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "mdm_entity_id")
    private Long masterDataEntityId;

    @Column(name = "check_name")
    private String checkName;

    private String status;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "passed_records")
    private Integer passedRecords;

    @Column(name = "failed_records")
    private Integer failedRecords;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static QualityCheck create(Long id, Long masterDataEntityId) {
        QualityCheck check = new QualityCheck();
        check.id = id;
        check.tenantId = 0L;
        check.masterDataEntityId = masterDataEntityId;
        check.checkName = "quality-check";
        check.startedAt = LocalDateTime.now();
        check.status = "RUNNING";
        check.createdAt = LocalDateTime.now();
        return check;
    }

    public void complete(Integer totalRecords, Integer passedRecords, Integer failedRecords) {
        this.endedAt = LocalDateTime.now();
        this.status = "COMPLETED";
        this.totalRecords = totalRecords;
        this.passedRecords = passedRecords;
        this.failedRecords = failedRecords;
        this.addDomainEvent(new QualityCheckCompletedEvent(this));
    }
}
