package com.bone.metadata.catalog.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.sdk.Repository;
import java.util.List;
import java.util.Optional;

/** 字段聚合仓储。读模型方法（ADR-0030）下沉至此；应用层调用而非持有读侧 DSL。 */
public interface MetaFieldRepository extends Repository<MetaField, Long> {

  default PageResult<MetaField> pageFields(Long entityId, String keyword, int page, int size) {
    // SDK 读侧不自动过滤软删；列表/校验必须显式排除 deleted 行（否则已删字段仍展示并参与重复检测）
    var query = query().where(MetaField::getDeleted).eq(false);
    if (entityId != null) {
      query = query.and(MetaField::getEntityId).eq(entityId);
    }
    if (keyword != null && !keyword.isBlank()) {
      query = query.and(MetaField::getName).like("%" + keyword.trim() + "%");
    }
    return query.orderByAsc(MetaField::getSortOrder).page(page, size);
  }

  default Optional<MetaField> findByEntityAndCode(Long entityId, String code) {
    return where(MetaField::getEntityId).eq(entityId).and(MetaField::getCode).eq(code).first();
  }

  /**
   * 实体的全部未删字段（选项源物化/漂移对比按实体批量走查）。
   *
   * <p>与 {@link #pageFields} 同为显式排除软删（SDK 读侧不过滤）；物化要全量而非首页，故走 {@code list()}。
   */
  default List<MetaField> findByEntityId(Long entityId) {
    return query()
        .where(MetaField::getEntityId)
        .eq(entityId)
        .and(MetaField::getDeleted)
        .eq(false)
        .orderByAsc(MetaField::getSortOrder)
        .list();
  }

  /**
   * 全部已绑定非 inline 真源的字段（引用方清单的候选集，界面设计器字典打通 v1.1 §5.4）。
   *
   * <p>只按 {@code enum_source IS NOT NULL} 粗筛（SQL 无法可靠解析 JSON 内的 ref.typeCode—— LIKE
   * 通配符与序列化空格都会造成漏判/误判），精确匹配由应用层解析声明后完成。 dict 绑定字段总量远小于全表，粗筛 + 内存精判的代价可忽略；显式排除软删。
   */
  default List<MetaField> listDictBoundFields() {
    return query()
        .where(MetaField::getEnumSource)
        .isNotNull()
        .and(MetaField::getDeleted)
        .eq(false)
        .list();
  }

  /**
   * 全部未删 ENUM 字段（绑定覆盖率的分母，界面设计器字典打通 v1.1 §8 可观测指标）。
   *
   * <p>覆盖率 = 已绑定真源的 ENUM 字段 / 全部 ENUM 字段：分母必须是「全量 ENUM」而不是「已绑定」， 否则指标恒为 100%，长期看不到 L0
   * 长尾——指标失去判别力比没有指标更糟。
   */
  default List<MetaField> listEnumFields() {
    return query().where(MetaField::getType).eq("ENUM").and(MetaField::getDeleted).eq(false).list();
  }
}
