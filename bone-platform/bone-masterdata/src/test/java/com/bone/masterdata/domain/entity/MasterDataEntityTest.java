package com.bone.masterdata.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.entity.event.MasterDataEntityCreatedEvent;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityStatus;
import org.junit.jupiter.api.Test;

/** {@link MasterDataEntity} 纯单测：DRAFT 起步、发布防重、已发布实体不可再改（无容器）。 */
class MasterDataEntityTest {

  private MasterDataEntity createEntity() {
    return MasterDataEntity.create(1L, 100L, MasterDataEntityName.of("客户主数据"), "客户域", "customer");
  }

  @Test
  void testCreateStartsAtDraftAndPublishesEvent() {
    MasterDataEntity entity = createEntity();

    assertEquals(MasterDataEntityStatus.DRAFT, entity.getStatus());
    assertEquals(100L, entity.getMetaEntityId());
    assertEquals("customer", entity.getCategory());
    assertEquals(1, entity.getDomainEvents().size());
    assertInstanceOf(MasterDataEntityCreatedEvent.class, entity.getDomainEvents().get(0));
  }

  @Test
  void testPublishTransitionsAndGuardsDuplicatePublish() {
    MasterDataEntity entity = createEntity();

    entity.publish();

    assertEquals(MasterDataEntityStatus.PUBLISHED, entity.getStatus());
    assertThrows(DomainException.class, entity::publish);
  }

  @Test
  void testUpdateAllowedBeforePublishButRejectedAfter() {
    MasterDataEntity entity = createEntity();

    entity.update(MasterDataEntityName.of("客户主数据v2"), "新描述", "customer");
    assertEquals("客户主数据v2", entity.getName().value());

    entity.publish();
    assertThrows(
        DomainException.class,
        () -> entity.update(MasterDataEntityName.of("客户主数据v3"), "改", "customer"));
  }
}
