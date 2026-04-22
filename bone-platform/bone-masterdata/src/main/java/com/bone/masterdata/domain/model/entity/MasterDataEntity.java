package com.bone.masterdata.domain.model.entity;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.entity.event.MasterDataEntityCreatedEvent;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityStatus;
import com.bone.core.util.DistributedIdGenerator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterDataEntity extends AggregateRoot<MasterDataEntityId> {
    private MasterDataEntityId id;
    private Long dbId;
    private MasterDataEntityName name;
    private String description;
    private String category;
    private MasterDataEntityStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static MasterDataEntity create(MasterDataEntityName name, String description, String category) {
        MasterDataEntity entity = new MasterDataEntity();
        entity.id = MasterDataEntityId.of(DistributedIdGenerator.generateUuid());
        entity.name = name;
        entity.description = description;
        entity.category = category;
        entity.status = MasterDataEntityStatus.DRAFT;
        entity.createTime = LocalDateTime.now();
        entity.updateTime = LocalDateTime.now();
        entity.addDomainEvent(new MasterDataEntityCreatedEvent(entity));
        return entity;
    }

    public void publish() {
        if (this.status == MasterDataEntityStatus.PUBLISHED) {
            throw new DomainException("主数据实体已发布");
        }
        this.status = MasterDataEntityStatus.PUBLISHED;
        this.updateTime = LocalDateTime.now();
    }

    public void update(MasterDataEntityName name, String description, String category) {
        if (this.status == MasterDataEntityStatus.PUBLISHED) {
            throw new DomainException("已发布的主数据实体不能修改");
        }
        this.name = name;
        this.description = description;
        this.category = category;
        this.updateTime = LocalDateTime.now();
    }

    // 仅供 SDK 回填 DB ID 使用
    void setDbId(Long dbId) {
        this.dbId = dbId;
    }
}