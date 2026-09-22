package com.bone.system.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.vo.ConfigKey;
import com.bone.system.domain.model.config.vo.ConfigType;
import java.util.List;
import java.util.Optional;

/**
 * 系统配置仓储端口：写侧 + <b>本聚合读</b>（ADR-0030 合并后，`domain.repository` 是本聚合读方法的唯一落点）。
 *
 * <p><b>为什么 DSL 出现在这一层</b>：`Criteria` / `QueryBuilder` 是 SDK 的持久化集成点，`domain.repository` 是唯一允许触碰它们的
 * domain 包（E-4.2）；application 不得再拼 DSL，否则 `readSideDslOnlyInQueryLayer` 会被绕过。 Criteria
 * 承载租户与软删自动注入，这里是**有护栏的那一层**。
 *
 * <p><b>通道选择</b>：能用 Criteria 表达的（按业务键取单个聚合）一律用 Criteria；需要跨列 OR 的分页/列表才降级到
 * `QueryBuilder`——`Criteria.or(Consumer)` 生成的条件片段会被当作等值条件拼接 （`(a LIKE :x OR b LIKE :y) = :null`），是
 * SDK 已知缺陷，在其修复前不使用。
 */
public interface SystemConfigRepository extends Repository<SystemConfig, Long> {

  /**
   * 按配置键加载本租户的配置；不存在或跨租户不可见时返回 {@code Optional.empty()}。
   *
   * <p>写用例用它做「键唯一」的前置判断；查得 entity 而不是 boolean——带上 id 能让后续 upsert 少一次查询。
   */
  default Optional<SystemConfig> findByConfigKey(ConfigKey configKey) {
    return Optional.ofNullable(
        findOneByCriteria(
            Criteria.<SystemConfig>create()
                .entityClass(SystemConfig.class)
                .eq(SystemConfig::getConfigKey, configKey)));
  }

  /**
   * 关键字分页 + 配置类型精确过滤：命中配置键或描述任一列即计入（OR）。
   *
   * @param keyword 为空或空白时不加过滤条件，等价于全量分页；{@code configType} 为空时不过滤类型
   */
  default PageResult<SystemConfig> pageByKeyword(
      String keyword, ConfigType configType, int pageNum, int pageSize) {
    boolean hasKeyword = keyword != null && !keyword.isBlank();
    var query = QueryBuilder.from(SystemConfig.class);
    if (hasKeyword) {
      query
          .where(SystemConfig::getConfigKey)
          .like(keyword)
          .or(SystemConfig::getDescription)
          .like(keyword);
    }
    if (configType != null) {
      query.and(SystemConfig::getConfigType).eq(configType);
    }
    return query.orderByDesc(SystemConfig::getCreatedAt).page(pageNum, pageSize);
  }

  /**
   * 全量配置（按配置键升序），供快照导出使用。
   *
   * <p>不分页：配置是业务配置项而非流水数据，量级有限，导出要求一次拿到完整集合。若增长到千级以上， 应改为按 {@code configKey} 游标分批，而不是调大某个 limit。
   */
  default List<SystemConfig> findAllOrderedByKey() {
    return QueryBuilder.from(SystemConfig.class).orderBy(SystemConfig::getConfigKey, true).list();
  }
}
