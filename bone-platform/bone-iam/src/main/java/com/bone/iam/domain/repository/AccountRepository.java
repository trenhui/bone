package com.bone.iam.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.Optional;

/**
 * 账号聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>本聚合的读模型驻留此处，不另设 {@code *QueryPort}——账号的读与写模型没有分歧，建端口只是仪式性分层（E-3.2）。
 */
public interface AccountRepository extends Repository<Account, Long> {

  /**
   * 登录入口：按用户名跨租户定位账号（<b>全租户通道</b>）。
   *
   * <p><b>为什么必须关掉租户过滤</b>：登录请求尚未认证，JWT 过滤器不会写 {@code TenantContext}；而 {@code iam_account} 是租户表，按
   * ADR-0029 的失败关闭语义，带租户过滤的查询会抛 {@link
   * com.bone.metadata.sdk.domain.exception.MissingTenantContextException}（新 SDK 落地后登录直接
   * 500）。登录时租户恰恰是 <b>待求解的未知量</b>——先找到账号才知道它属于哪个租户，所以这里只能全租户查找。
   *
   * <p><b>已知前提与代价（务必读完再用）</b>：{@code bone-init.sql} 的唯一键是 {@code uk_iam_account_username
   * (tenant_id, username)}，即<b>唯一性只在租户内成立，跨租户允许重名</b>。因此本方法命中多行时会抛 {@link
   * com.bone.metadata.sdk.domain.exception.MultipleResultsException}——调用方必须把它当「登录无法确定租户」显式处理 （记
   * ERROR + 401），<b>不得静默转成"用户不存在"</b>，否则两个租户一旦重名，两边的账号会同时永久登录不上且无任何痕迹。 长期方案是登录请求携带租户标识（tenantCode /
   * 域名）后按 {@code (username, tenantId)} 查询，届时应删除本方法。
   *
   * <p><b>只限登录入口调用</b>：建账号等已认证路径请用 {@link #findByUsernameInTenant(String)}，它们的租户上下文是确定的。
   *
   * <p><b>后续</b>：查到账号后，写操作（失败计数 / 成功清零）与权限查询必须经 {@code
   * com.bone.core.tenant.context.TenantContextRunner} 按 {@code account.getTenantId()}
   * 显式声明租户（ADR-0031 D3）。
   */
  default Optional<Account> findByUsernameForLoginAllTenants(String username) {
    return Optional.ofNullable(
        findOneByCriteria(
            Criteria.<Account>create().eq("username", username).disableTenantFilter()));
  }

  /**
   * 按用户名在<b>当前租户</b>内定位账号（建账号查重等已认证路径）。
   *
   * <p>不关租户过滤：这些路径的 {@code TenantContext} 由 JWT 过滤器写入，租户是已知量； 若改用 {@link
   * #findByUsernameForLoginAllTenants(String)}，查重会从「本租户唯一」漂移到「全平台唯一」， 与 {@code
   * uk_iam_account_username (tenant_id, username)} 的语义直接冲突（不同租户同名会被误判 409）。
   */
  default Optional<Account> findByUsernameInTenant(String username) {
    return Optional.ofNullable(
        findOneByCriteria(Criteria.<Account>create().eq("username", username)));
  }

  /**
   * 账号分页（本聚合读）。
   *
   * <p>关键字跨 {@code username} / {@code email} / {@code realName} 三列 OR，须用 {@link
   * QueryBuilder}；租户过滤值由调用方解析后显式传入。
   *
   * @param tenantId 已解析的租户过滤值；{@code null} 表示不加租户条件
   */
  default PageResult<Account> findAccountPage(
      String keyword, AccountStatus status, Long tenantId, int pageNo, int pageSize) {
    FluentQuery<Account> query = QueryBuilder.from(Account.class);
    if (keyword != null && !keyword.isEmpty()) {
      query
          .where(Account::getUsername)
          .like(keyword)
          .or(Account::getEmail)
          .like(keyword)
          .or(Account::getRealName)
          .like(keyword);
    }
    if (status != null) {
      query.where(Account::getStatus).eq(status);
    }
    if (tenantId != null) {
      query.where(Account::getTenantId).eq(tenantId);
    }
    return query.orderByDesc(Account::getCreatedAt).page(pageNo, pageSize);
  }

  /** 某租户账号数（配额校验用，本聚合读）。 */
  default long countByTenant(Long tenantId) {
    return countByCriteria(Criteria.<Account>create().eq("tenantId", tenantId));
  }
}
