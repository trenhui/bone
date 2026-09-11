package com.bone.masterdata.domain.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.record.event.MasterDataRecordCreatedEvent;
import com.bone.masterdata.domain.model.record.event.MasterDataRecordPublishedEvent;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordStatus;
import org.junit.jupiter.api.Test;

/** {@link MasterDataRecord} 纯单测：DRAFT 起步、发布防重与已发布记录只读语义（无容器）。 */
class MasterDataRecordTest {

  private MasterDataRecord createRecord() {
    return MasterDataRecord.create(1L, 100L, "{\"customerId\":1}");
  }

  @Test
  void testCreateStartsAtDraftAndPublishesCreatedEvent() {
    MasterDataRecord record = createRecord();

    assertEquals(MasterDataRecordStatus.DRAFT, record.getStatus());
    assertEquals(100L, record.getMasterDataEntityId());
    assertEquals(1, record.getDomainEvents().size());
    assertInstanceOf(MasterDataRecordCreatedEvent.class, record.getDomainEvents().get(0));
  }

  @Test
  void testPublishStampsTimeAndPublishesEventGuardingDuplicate() {
    MasterDataRecord record = createRecord();
    record.clearDomainEvents();

    record.publish();

    assertEquals(MasterDataRecordStatus.PUBLISHED, record.getStatus());
    assertNotNull(record.getPublishTime());
    assertEquals(1, record.getDomainEvents().size());
    assertInstanceOf(MasterDataRecordPublishedEvent.class, record.getDomainEvents().get(0));

    assertThrows(DomainException.class, record::publish);
  }

  @Test
  void testUpdateAllowedWhileDraftButRejectedAfterPublish() {
    MasterDataRecord record = createRecord();

    record.update("{\"customerId\":2}");
    assertEquals("{\"customerId\":2}", record.getData());

    record.publish();
    assertThrows(DomainException.class, () -> record.update("{\"customerId\":3}"));
  }
}
