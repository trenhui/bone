package com.bone.masterdata.domain.record;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.record.event.MasterDataRecordCreatedEvent;
import com.bone.masterdata.domain.model.record.event.MasterDataRecordPublishedEvent;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordStatus;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("md_record")
public class MasterDataRecord extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "master_data_entity_id")
  private Long masterDataEntityId;

  private String data;
  private MasterDataRecordStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime publishTime;

  public static MasterDataRecord create(Long id, Long masterDataEntityId, String data) {
    MasterDataRecord record = new MasterDataRecord();
    record.id = id;
    record.masterDataEntityId = masterDataEntityId;
    record.data = data;
    record.status = MasterDataRecordStatus.DRAFT;
    record.createdAt = LocalDateTime.now();
    record.updatedAt = LocalDateTime.now();
    record.addDomainEvent(new MasterDataRecordCreatedEvent(record));
    return record;
  }

  public void publish() {
    if (this.status == MasterDataRecordStatus.PUBLISHED) {
      throw new DomainException("主数据记录已发布");
    }
    this.status = MasterDataRecordStatus.PUBLISHED;
    this.publishTime = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    this.addDomainEvent(new MasterDataRecordPublishedEvent(this));
  }

  public void update(String data) {
    if (this.status == MasterDataRecordStatus.PUBLISHED) {
      throw new DomainException("已发布的主数据记录不能修改");
    }
    this.data = data;
    this.updatedAt = LocalDateTime.now();
  }

  public void archive() {
    if (this.status == MasterDataRecordStatus.ARCHIVED) {
      throw new DomainException("主数据记录已归档");
    }
    this.status = MasterDataRecordStatus.ARCHIVED;
    this.updatedAt = LocalDateTime.now();
  }
}
