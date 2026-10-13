package com.bone.metadata.catalog.domain.model.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 实体聚合的纯领域单测（无容器、无 Mock）。 */
class MetaEntityTest {

  private static MetaEntity draftEntity() {
    return MetaEntity.create(1001L, 0L, "订单", "order", "订单", "订单实体", "t_order", 0, 0, "icon", null);
  }

  /** 草稿实体删除时释放唯一键占位，让「建错了重建」可再次使用同一编码。 */
  @Test
  void draftEntityReleasesUniqueKeysOnDelete() {
    MetaEntity entity = draftEntity();

    assertTrue(entity.releaseUniqueKeysForDelete());
    assertEquals("order_del_1001", entity.getCode());
    assertEquals("t_order_del_1001", entity.getTableName());
  }

  /** 已发布实体可能已生成物理表/代码产物，编码必须保持占用，否则复用会与残留产物冲突。 */
  @Test
  void publishedEntityKeepsUniqueKeysOnDelete() {
    MetaEntity entity = draftEntity();
    entity.publish();

    assertFalse(entity.releaseUniqueKeysForDelete());
    assertEquals("order", entity.getCode());
    assertEquals("t_order", entity.getTableName());
  }

  /** 幂等：重复调用不再追加后缀（唯一键释放只会发生在删除路径，防重复仍要成立）。 */
  @Test
  void releaseIsIdempotent() {
    MetaEntity entity = draftEntity();

    entity.releaseUniqueKeysForDelete();
    entity.releaseUniqueKeysForDelete();

    assertEquals("order_del_1001", entity.getCode());
  }

  /** 0043：create 12 参重载必须把 auto_number 配置落到聚合（后续由 catalog provider 透传进 runtime）。 */
  @Test
  void createCarriesAutoNumberConfig() {
    MetaEntity e =
        MetaEntity.create(
            1002L,
            0L,
            "报销",
            "claim",
            "报销",
            "报销实体",
            "t_claim",
            0,
            1,
            "icon",
            null,
            "{\"field\":\"claim_no\",\"prefix\":\"CLM\",\"reset\":\"daily\"}");
    assertEquals(
        "{\"field\":\"claim_no\",\"prefix\":\"CLM\",\"reset\":\"daily\"}", e.getAutoNumber());
  }

  /** 0043：草稿实体 update 必须把 auto_number 配置落到聚合。 */
  @Test
  void updateCarriesAutoNumberConfigOnDraft() {
    MetaEntity e = draftEntity();
    e.update(
        "报销",
        "报销",
        "报销实体",
        "t_claim",
        0,
        "icon",
        1,
        "{\"field\":\"claim_no\",\"prefix\":\"CLM\",\"reset\":\"daily\"}");
    assertEquals(
        "{\"field\":\"claim_no\",\"prefix\":\"CLM\",\"reset\":\"daily\"}", e.getAutoNumber());
  }

  /** 0043：草稿实体 update 传 null 应清空 auto_number（关闭自动编号）。 */
  @Test
  void updateClearsAutoNumberWhenNull() {
    MetaEntity e =
        MetaEntity.create(
            1003L,
            0L,
            "报销",
            "claim2",
            "报销",
            "报销实体",
            "t_claim2",
            0,
            1,
            "icon",
            null,
            "{\"field\":\"claim_no\",\"prefix\":\"CLM\",\"reset\":\"none\"}");
    e.update("报销", "报销", "报销实体", "t_claim2", 0, "icon", 1, null);
    assertNull(e.getAutoNumber());
  }
}
