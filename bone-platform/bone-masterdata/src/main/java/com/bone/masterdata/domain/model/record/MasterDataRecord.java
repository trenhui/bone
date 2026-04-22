package com.bone.masterdata.domain.model.record;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.record.event.MasterDataRecordCreatedEvent;
import com.bone.masterdata.domain.model.record.event.MasterDataRecordPublishedEvent;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordId;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterDataRecord extends AggregateRoot<MasterDataRecordId> {
    private MasterDataEntityId masterDataEntityId;
    private String data;
    private MasterDataRecordStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime publishTime;

    public static MasterDataRecord create(MasterDataEntityId masterDataEntityId, String data) {
        MasterDataRecord record = new MasterDataRecord();
        record.masterDataEntityId = masterDataEntityId;
        record.data = data;
        record.status = MasterDataRecordStatus.DRAFT;
        record.createTime = LocalDateTime.now();
        record.updateTime = LocalDateTime.now();
        record.addDomainEvent(new MasterDataRecordCreatedEvent(record));
        return record;
    }

    public void publish() {
        if (this.status == MasterDataRecordStatus.PUBLISHED) {
            throw new DomainException("主数据记录已发布");
        }
        this.status = MasterDataRecordStatus.PUBLISHED;
        this.publishTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.addDomainEvent(new MasterDataRecordPublishedEvent(this));
    }

    public void update(String data) {
        if (this.status == MasterDataRecordStatus.PUBLISHED) {
            throw new DomainException("已发布的主数据记录不能修改");
        }
        this.data = data;
        this.updateTime = LocalDateTime.now();
    }

    // 仅供 SDK 回填 ID 使用
    void setId(MasterDataRecordId id) {
        super.setId(id);
    }
}