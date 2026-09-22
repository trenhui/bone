package com.bone.system.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.domain.model.dict.SysDict;
import com.bone.system.domain.model.dict.valueobject.DictType;
import java.util.List;
import java.util.Optional;

/**
 * 系统字典仓储端口：写侧 + 本聚合读（ADR-0030）。
 *
 * <p><b>通道选择</b>见 {@link SystemConfigRepository} 的说明：单键值判定走 Criteria（自带租户与软删过滤）， 跨列 OR 的分页走
 * `QueryBuilder`。
 */
public interface SysDictRepository extends Repository<SysDict, Long> {

  /** 同字典类型下按 code 加载字典项；不存在时返回 {@code Optional.empty()}。 */
  default Optional<SysDict> findByTypeAndCode(DictType type, String code) {
    return Optional.ofNullable(
        findOneByCriteria(
            Criteria.<SysDict>create()
                .entityClass(SysDict.class)
                .eq(SysDict::getType, type)
                .eq(SysDict::getCode, code)));
  }

  /** 按字典类型取全部字典项（前端下拉框用，不分页——单类型字典项数量有限）。 */
  default List<SysDict> findByType(DictType type) {
    return QueryBuilder.from(SysDict.class).where(SysDict::getType).eq(type).list();
  }

  /**
   * 字典项分页：类型精确过滤 + 关键字模糊命中 label / code。
   *
   * @param type 为空时不过滤类型；{@code keyword} 为空或空白时不加关键字条件
   */
  default PageResult<SysDict> pageByTypeAndKeyword(
      DictType type, String keyword, int pageNum, int pageSize) {
    var query = QueryBuilder.from(SysDict.class);
    if (type != null) {
      query.where(SysDict::getType).eq(type);
    }
    boolean hasKeyword = keyword != null && !keyword.isBlank();
    if (hasKeyword) {
      query.where(SysDict::getLabel).like(keyword).or(SysDict::getCode).like(keyword);
    }
    return query.page(pageNum, pageSize);
  }
}
