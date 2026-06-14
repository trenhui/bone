package com.bone.masterdata.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.entity.event.MasterDataEntityCreatedEvent;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityStatus;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("md_entity")
public class MasterDataEntity extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "meta_entity_id")
  private Long metaEntityId;

  private MasterDataEntityName name;
  private String description;
  private String category;
  private MasterDataEntityStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static MasterDataEntity create(
      Long id, Long metaEntityId, MasterDataEntityName name, String description, String category) {
    MasterDataEntity entity = new MasterDataEntity();
    entity.id = id;
    entity.metaEntityId = metaEntityId;
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
