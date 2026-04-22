package com.bone.masterdata.domain.model.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.quality.event.QualityCheckCompletedEvent;
import com.bone.masterdata.domain.model.quality.vo.QualityCheckId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QualityCheck extends AggregateRoot<QualityCheckId> {
    private QualityCheckId id;
    private MasterDataEntityId masterDataEntityId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer totalRecords;
    private Integer passedRecords;
    private Integer failedRecords;
    private LocalDateTime createTime;

    public static QualityCheck create(MasterDataEntityId masterDataEntityId) {
        QualityCheck check = new QualityCheck();
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

    // 仅供 SDK 回填 ID 使用
    @Override
    public void setId(QualityCheckId id) { this.id = id; }
}