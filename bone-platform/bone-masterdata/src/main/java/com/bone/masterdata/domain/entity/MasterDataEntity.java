package com.bone.masterdata.domain.entity;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.entity.event.MasterDataEntityCreatedEvent;
import com.bone.masterdata.domain.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.entity.vo.MasterDataEntityStatus;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("md_entity")
public class MasterDataEntity extends AggregateRoot<Long> {
    private Long id;
    private MasterDataEntityName name;
    private String description;
    private String category;
    private MasterDataEntityStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MasterDataEntity create(Long id, MasterDataEntityName name, String description, String category) {
        MasterDataEntity entity = new MasterDataEntity();
        entity.id = id;
        entity.name = name;
        entity.description = description;
        entity.category = category;
        entity.status = MasterDataEntityStatus.DRAFT;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.addDomainEvent(new MasterDataEntityCreatedEvent(entity));
        return entity;
    }

    public void publish() {
        if (this.status == MasterDataEntityStatus.PUBLISHED) {
            throw new DomainException("主数据实体已发布");
        }
        this.status = MasterDataEntityStatus.PUBLISHED;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(MasterDataEntityName name, String description, String category) {
        if (this.status == MasterDataEntityStatus.PUBLISHED) {
            throw new DomainException("已发布的主数据实体不能修改");
        }
        this.name = name;
        this.description = description;
        this.category = category;
        this.updatedAt = LocalDateTime.now();
    }
}
