package com.bone.core.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.junit.jupiter.api.Test;

/** AbstractEntity 实体基类测试：默认值、审计字段、软删除 */
class AbstractEntityTest {

  @Test
  void defaultConstructor_setsSoftDeleteFalse() {
    TestEntity entity = new TestEntity();
    assertThat(entity.getDeleted()).isFalse();
    assertThat(entity.getId()).isNull();
    assertThat(entity.getCreatedAt()).isNull();
    assertThat(entity.getCreatedBy()).isNull();
    assertThat(entity.getUpdatedAt()).isNull();
    assertThat(entity.getUpdatedBy()).isNull();
  }

  @Test
  void allArgsConstructor_populatesFields() {
    Date now = new Date();
    TestEntity entity = new TestEntity(1L, now, 100L, now, 100L, true);
    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getCreatedBy()).isEqualTo(100L);
    assertThat(entity.getDeleted()).isTrue();
  }

  @Test
  void setters_updateAuditFields() {
    TestEntity entity = new TestEntity();
    Date now = new Date();
    entity.setCreatedAt(now);
    entity.setCreatedBy(7L);
    entity.setUpdatedAt(now);
    entity.setUpdatedBy(8L);
    entity.setDeleted(true);

    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getCreatedBy()).isEqualTo(7L);
    assertThat(entity.getUpdatedAt()).isEqualTo(now);
    assertThat(entity.getUpdatedBy()).isEqualTo(8L);
    assertThat(entity.getDeleted()).isTrue();
  }

  @Test
  void implementsSoftDeletableAndAuditable() {
    TestEntity entity = new TestEntity();
    assertThat(entity).isInstanceOf(SoftDeletable.class);
    assertThat(entity).isInstanceOf(Auditable.class);
  }

  /** 测试用实体：以 Long 为主键的 AbstractEntity 子类 */
  static class TestEntity extends AbstractEntity<Long> {
    public TestEntity() {
      super();
    }

    public TestEntity(
        Long id, Date createdAt, Long createdBy, Date updatedAt, Long updatedBy, Boolean deleted) {
      super(id, createdAt, createdBy, updatedAt, updatedBy, deleted);
    }
  }
}
