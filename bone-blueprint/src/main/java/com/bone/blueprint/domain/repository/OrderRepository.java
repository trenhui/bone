package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.shared.exception.OptimisticLockConflictException;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 订单写侧仓储端口。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>多租户隔离下沉到仓储查询层：{@link #findByIdInTenant} 用通用条件查询在 SQL 层附加租户条件，跨租户订单直接被 过滤（返回 {@code
 * null}），而非加载后手动校验。
 *
 * <p><b>乐观锁更新（E-5.3）</b>：{@link #saveWithVersionCheck} 用 SDK {@link Criteria} 组装 {@code WHERE id =
 * ? AND version = ?} 条件更新，行数为 0 即表示被并发修改，抛出 {@link OptimisticLockConflictException}。
 *
 * <p><b>为什么 Repository 层能用 Criteria（@ReadSideOnly）</b>：Repository 是 SDK 框架集成点，基类 {@link Repository}
 * 本身就声明了 {@code updateByCriteria(Criteria<T>)} 方法签名。domain 层 Repository 子类 继承该签名、只在"版本条件更新"这一写侧场景使用
 * Criteria——和业务代码主动依赖读侧 DSL 做查询有本质区别， 已在 {@code ArchitectureTest#domain_no_query_builder} 中对 {@code
 * ..domain.repository..} 包做了豁免。
 */
public interface OrderRepository extends Repository<Order, Long> {

  /**
   * 按 id 加载当前租户可访问的订单。
   *
   * <p>仅返回 {@code id} 与 {@code tenantId} 同时匹配、且未被软删的订单；跨租户或不存在时返回 {@code null}（与 {@link #findById}
   * 的空语义一致）。
   *
   * <p><b>租户隔离下沉到 SQL 层（失败关闭）</b>：用 {@code findOneByCriteria} 把 id 与 tenantId 两条 {@code EQ} 条件一并下发到
   * SELECT，跨租户订单在数据库侧被过滤（而非加载后内存校验）。软删过滤由 SDK 默认排除 {@code deleted} 保证，与 {@link #findById} 一致。这一不变量由
   * {@code RepositoryTenantIsolationTest} 白盒锁死： 缺租户时返回 null、且不触达查询。相比 {@code
   * queryByCondition(...,1,1)}，{@code findOneByCriteria} 只发一条 SELECT、不触发 {@code
   * countByCriteria}（分页整页命中会额外计数），且与本类 {@link #saveWithVersionCheck} 的 {@code Criteria} 风格一致。
   *
   * <p><b>前置判空不是冗余防御</b>：SDK 条件构造（{@code Criteria.eq}）会<strong>静默丢弃</strong> 值为 {@code null}
   * 的条件。若租户缺失，WHERE 中的租户条件会整条消失，查询退化为「按 id 跨租户读取」——多租户隔离在此<strong>失败开启</strong>。
   * 两者语义<strong>并不等价</strong>，故显式补回：租户不可知即视为不可访问。
   */
  default Order findByIdInTenant(Long id, Long tenantId) {
    if (id == null || tenantId == null) {
      return null;
    }
    return findOneByCriteria(Criteria.<Order>create().eq("id", id).eq("tenantId", tenantId));
  }

  /**
   * 乐观锁条件更新（E-5.3 并发护栏）。
   *
   * <p>前置条件：聚合行为方法须先调用 {@code incrementVersion()} 让 {@code entity.version} 递增为新值。 本方法以 {@code
   * entity.version - 1} 作为 WHERE 条件里的期望旧版本值，数据库层面保证：
   *
   * <pre>{@code
   * UPDATE t_order SET ..., version = :entity.version  WHERE id = :entity.id AND version = :entity.version - 1
   * }</pre>
   *
   * @param entity 已做状态变更 + incrementVersion 的聚合实例
   * @throws OptimisticLockConflictException WHERE 条件命中 0 行时抛出（说明已被并发修改）
   */
  default void saveWithVersionCheck(Order entity) {
    Long version = entity.getVersion();
    long whereVersion = (version != null && version > 0) ? version - 1 : 0;
    Criteria<Order> criteria =
        Criteria.<Order>create().eq("id", entity.getId()).eq("version", whereVersion);
    int affectedRows = updateByCriteria(entity, criteria);
    if (affectedRows == 0) {
      throw new OptimisticLockConflictException("Order", entity.getId(), whereVersion);
    }
  }
}
