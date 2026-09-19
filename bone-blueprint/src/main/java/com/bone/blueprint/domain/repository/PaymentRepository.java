package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.shared.exception.OptimisticLockConflictException;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 支付单写侧仓储端口。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>多租户隔离下沉到仓储查询层：{@link #findByIdInTenant} 用通用条件查询在 SQL 层附加租户条件（与 {@code
 * OrderRepository.findByIdInTenant} 同模式）。
 *
 * <p><b>乐观锁更新（E-5.3）</b>：{@link #saveWithVersionCheck} 用 SDK {@link Criteria} 组装 {@code WHERE id =
 * ? AND version = ?} 条件更新，行数为 0 即表示被并发修改，抛出 {@link OptimisticLockConflictException}。
 *
 * <p><b>为什么 Repository 层能用 Criteria（@ReadSideOnly）</b>：同 {@link OrderRepository}——Repository 是 SDK
 * 框架集成点，基类自带 {@code updateByCriteria(Criteria<T>)} 方法签名。
 */
public interface PaymentRepository extends Repository<Payment, Long> {

  /**
   * 按 id 加载当前租户可访问的支付单。
   *
   * <p>仅返回 {@code id} 与 {@code tenantId} 同时匹配、且未被软删的支付单；跨租户或不存在时返回 {@code null}。
   *
   * <p><b>租户隔离下沉到 SQL 层（失败关闭）</b>：用 {@code findOneByCriteria} 把 id 与 tenantId 两条 {@code EQ} 条件一并下发到
   * SELECT，跨租户支付单在数据库侧被过滤（而非加载后内存校验）。软删过滤由 SDK 默认排除 {@code deleted} 保证，与 {@link #findById} 一致。相比
   * {@code queryByCondition(...,1,1)}，{@code findOneByCriteria} 只发一条 SELECT、不触发 {@code
   * countByCriteria}（分页整页命中会额外计数），且与本类 {@link #saveWithVersionCheck} 的 {@code Criteria} 风格一致。
   *
   * <p><b>前置判空不是冗余防御，而是失败关闭</b>：{@code Criteria.eq} 会静默丢弃 {@code null} 值条件，
   * 租户缺失时查询退化为跨租户读取——失败开启。故显式补回判空：租户不可知即视为不可访问。
   */
  default Payment findByIdInTenant(Long id, Long tenantId) {
    if (id == null || tenantId == null) {
      return null;
    }
    return findOneByCriteria(Criteria.<Payment>create().eq("id", id).eq("tenantId", tenantId));
  }

  /**
   * 乐观锁条件更新（E-5.3 并发护栏）。
   *
   * <p>前置条件：聚合行为方法须先调用 {@code incrementVersion()} 让 {@code entity.version} 递增为新值。 本方法以 {@code
   * entity.version - 1} 作为 WHERE 条件里的期望旧版本值。
   *
   * @param entity 已做状态变更 + incrementVersion 的聚合实例
   * @throws OptimisticLockConflictException WHERE 条件命中 0 行时抛出（说明已被并发修改）
   */
  default void saveWithVersionCheck(Payment entity) {
    Long version = entity.getVersion();
    long whereVersion = (version != null && version > 0) ? version - 1 : 0;
    Criteria<Payment> criteria =
        Criteria.<Payment>create().eq("id", entity.getId()).eq("version", whereVersion);
    int affectedRows = updateByCriteria(entity, criteria);
    if (affectedRows == 0) {
      throw new OptimisticLockConflictException("Payment", entity.getId(), whereVersion);
    }
  }
}
