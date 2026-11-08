package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 保单仓储（{@code t_claim_policy}）。
 *
 * <p>读侧分两类：
 *
 * <ul>
 *   <li><b>配置面</b>（分页/模糊查/按 ID 查）：走 SDK {@link Criteria} DSL + 领域投影，供运营后台使用；
 *   <li><b>业务面</b>（按保单号查生效中的保单）：走 SQL 模板，供报案前置校验与额度年度裁决使用。
 * </ul>
 *
 * <p><b>为什么业务面不用 Criteria</b>：报案前置校验要"此刻生效中"这个时间相关判据， 而"生效起 ≤ now ≤ 生效止 + 规则启用 + 未过期"三条要一起判；写成 SQL
 * 模板比用 DSL 拼更直白， 且与既有的 {@code PolicyQueryPort} 语义一一对应（该端口是防腐层，未来切真实保单系统时只动适配器）。
 *
 * <p>注意：本仓储方法均返回聚合根或聚合根列表，符合 ArchUnit {@code domainRepositoriesShouldOnlyDeclareWhitelistedMethods}
 * 的返回类型白名单。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimPolicyRepository extends Repository<ClaimPolicy, Long> {

  /** 按保单号查（不按"此刻生效中"过滤）。 */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimPolicy findByPolicyNo(@Param("policyNo") String policyNo);

  /**
   * 按保单号查<b>此刻可用</b>的保单（生效期内 + 规则启用 + 未过期）。
   *
   * <p>报案前置校验用。查不到即拒收（{@code SC_CLAIM_POLICY_NOT_ACTIVE}）。
   */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimPolicy findUsablePolicy(@Param("policyNo") String policyNo);

  /**
   * 按保司侧保单号查<b>全部</b>匹配保单（§16-2 双保单号方案 B：policy_no 为平台号，insurer_policy_no 为保司号）。
   *
   * <p><b>不按"此刻生效中"过滤</b>，只用于对账/检索这类"该保司号历史上对应过哪些平台保单"的问题。
   * <b>收单前置校验不要用本方法</b>——已满期/已停用/快照过期的保单也会被查出来，直接放行会把"过期保单还能报案" 这个错误变成金额风险；收单侧必须用 {@link
   * #findUsablePoliciesByInsurerNo}。
   *
   * <p>返回列表：insurer_policy_no 未加唯一约束（同一保司号可对应多张平台保单，团险尤甚）， 调用方不得假设唯一。
   */
  default List<ClaimPolicy> findByInsurerPolicyNo(String insurerPolicyNo) {
    return findByCriteria(
        Criteria.<ClaimPolicy>create().eq(ClaimPolicy::getInsurerPolicyNo, insurerPolicyNo));
  }

  /**
   * 按保司侧保单号查<b>此刻可用</b>的保单（生效期内 + 规则启用 + 快照未过期）。
   *
   * <p><b>为什么返回列表而不是单条</b>：{@code insurer_policy_no} 没有唯一约束——团险一张保司保单号
   * 可能对应多张平台保单（按成员/计划拆分），配置面也允许同一保司号在不同期间各建一张。这里若像 {@code findUsablePolicy} 那样 {@code LIMIT
   * 1}，就会在多命中时<b>静默取一条</b>，把"该赔哪张保单"变成数据库返回顺序 决定的偶然结果（金额直接记到错误的保单/额度池上）。故由调用方显式判定"唯一命中/多命中"。
   *
   * <p>排序 {@code effective_start DESC, id DESC}：多命中时给调用方一个稳定顺序（用于日志与人工核查时的可复现性），
   * 但<b>不得</b>拿"取第一条"当消歧规则——消歧必须走业务判据（被保人是否在承保名单内）。
   */
  @TenantScope(TenantScopeMode.AUTO)
  List<ClaimPolicy> findUsablePoliciesByInsurerNo(@Param("insurerPolicyNo") String insurerPolicyNo);

  /**
   * 分页查保单（配置面）。
   *
   * @param tenantId 租户
   * @param keyword 保单号/保司保单号/名称模糊匹配（可空）
   * @param configStatus 规则开关过滤（可空）
   * @param page 页码（从 1 起）
   * @param size 每页条数
   */
  default List<ClaimPolicy> findPolicyPage(
      Long tenantId, String keyword, String configStatus, int page, int size) {
    Criteria<ClaimPolicy> criteria = policyListCriteria(tenantId, keyword, configStatus);
    criteria.orderByDesc(ClaimPolicy::getCreatedAt).page(page, size);
    return findByCriteria(criteria);
  }

  /** 分页总数（与 {@link #findPolicyPage} 同口径）。 */
  default long countPolicyPage(Long tenantId, String keyword, String configStatus) {
    return countByCriteria(policyListCriteria(tenantId, keyword, configStatus));
  }

  /**
   * 同筛选条件的全量列表（CSV 导出用，不翻页）。
   *
   * <p>与 {@link #findPolicyPage} 共用 {@link #policyListCriteria}：导出必须与列表页所见一致 （同一套关键字/状态条件），否则"页面 3
   * 条、导出 300 条"。
   */
  default List<ClaimPolicy> findPolicyAll(Long tenantId, String keyword, String configStatus) {
    Criteria<ClaimPolicy> criteria = policyListCriteria(tenantId, keyword, configStatus);
    criteria.orderByDesc(ClaimPolicy::getCreatedAt);
    return findByCriteria(criteria);
  }

  /** 发布覆盖前确认用的受影响保单数（规则开关 ACTIVE 且挂该主体的保单）。 */
  default long countActiveByBizIdentity(Long tenantId, String bizIdentityCode) {
    return countByCriteria(
        Criteria.<ClaimPolicy>create()
            .eq(ClaimPolicy::getTenantId, tenantId)
            .eq(ClaimPolicy::getBizIdentityCode, bizIdentityCode)
            .eq(ClaimPolicy::getConfigStatus, "ACTIVE"));
  }

  /** 主体管理页展示用的关联保单数（不分规则开关口径——停用中的保单也是"已关联"）。 */
  default long countByBizIdentity(Long tenantId, String bizIdentityCode) {
    return countByCriteria(
        Criteria.<ClaimPolicy>create()
            .eq(ClaimPolicy::getTenantId, tenantId)
            .eq(ClaimPolicy::getBizIdentityCode, bizIdentityCode));
  }

  /**
   * 列表查询条件（list 与 count 共用，避免两端口径漂移）。
   *
   * <p><b>关键字必须跨三列 OR</b>：前端搜索框承诺「保单号 / 保司保单号 / 名称」三维搜索，API 契约同样写明 「保单号/名称模糊匹配」。此前实现只对 {@code
   * policy_no} 单列做 LIKE —— 按保单名称搜索恒返回 0 条， 属"注释宣称 X ≠ 代码做了 X"的契约违背；{@code insurer_policy_no} 则是
   * §16-2 落地后新出现的"只写得进、搜不出来"缺口（保司来单/对账只带保司自己的号，运营按该号检索必须有结果）。
   *
   * <p><b>为何用 {@link Criteria#or(Consumer)} 条件组而非扁平 {@code or()} 链</b>：条件组会把子条件括号包裹成一个原生片段， 与外层 AND
   * 安全组合为 {@code (policy_no LIKE ? OR policy_name LIKE ?) AND config_status = ?}； 扁平 {@code or()}
   * 链在后续还有 {@code and()} 时会被 SQL 优先级退化为 {@code a OR (b AND c)}—— 「仅保单号命中、却不满足规则开关过滤」的行会从 OR
   * 左支漏过过滤（参照 {@code bone-system} 的 {@code RepositoryKeywordOrSearchTest} 同类回归用例）。
   *
   * <p>排序固定 {@code createdAt} 倒序：自然序在 OR 查询下不保证跨页稳定，分页可能重复/丢行。
   *
   * @param tenantId 租户
   * @param keyword 关键字（空/空白视为不过滤）
   * @param configStatus 规则开关（空/空白视为不过滤）
   */
  static Criteria<ClaimPolicy> policyListCriteria(
      Long tenantId, String keyword, String configStatus) {
    Criteria<ClaimPolicy> criteria =
        Criteria.<ClaimPolicy>create().eq(ClaimPolicy::getTenantId, tenantId);
    boolean hasKeyword = keyword != null && !keyword.isBlank();
    if (hasKeyword) {
      String pattern = "%" + keyword.trim() + "%";
      criteria.or(
          sub ->
              sub.like(ClaimPolicy::getPolicyNo, pattern)
                  .like(ClaimPolicy::getPolicyName, pattern)
                  .like(ClaimPolicy::getInsurerPolicyNo, pattern));
    }
    criteria.eq(
        configStatus != null && !configStatus.isBlank(),
        ClaimPolicy::getConfigStatus,
        configStatus);
    return criteria;
  }
}
