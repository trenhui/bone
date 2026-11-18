package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.supplier.Supplier;
import java.util.List;
import java.util.Optional;

/**
 * Supplier 合作资格的出站端口（领域端口，语义不随 adapter 变化）。
 *
 * <p><b>为什么端口不带 DDL 语义</b>：生产实现由 L3 的 JDBC adapter 提供（{@code t_proc_*} 类型化字段表属 R-2，须架构审批）。
 * 本切片只提供内存实现用于测试与演示装配，因此能力开关默认关闭 —— 不暴露「能调用但落不了库」的假能力。
 *
 * <p><b>分页刻意用 offset/limit 而非框架 Page 类型</b>：domain 只表达「取第几段、多少条」这一事实，HTTP 分页契约由 adapter 层
 * 组装，避免把传输层语义泄进领域端口。
 */
public interface SupplierRepositoryPort {

  /** 取某租户下某统一身份的合作资格；未登记返回 empty。跨租户不可见。 */
  Optional<Supplier> findCurrent(long tenantId, String partyId);

  /** 按租户分页列举合作资格（按 partyId 字典序稳定排序，便于对账与快照比对）。 */
  List<Supplier> listByTenant(long tenantId, int offset, int limit);

  /** 某租户下合作资格总数（供 adapter 组装 PageResult）。 */
  long countByTenant(long tenantId);

  /**
   * 保存（新增或覆盖同 partyId 的当前版本）。
   *
   * <p><b>必须带期望版本</b>（2026-11-16 独立审查 I-02 修复）：无条件 save 会让两个并发迁移「后者覆盖前者」——
   * 付款冻结刚被财务侧解除，一个基于旧状态的域内迁移就能把 {@code PAYMENT_BLOCKED} 覆盖回 {@code ACTIVE_VENDOR}，
   * 状态机守卫拦不住这种覆盖，因为两次写各自都是合法迁移。期望版本不符时实现须抛冲突，由调用方重读重试。
   *
   * @param expectedVersion 写入前读到的版本；新增传 0
   */
  void save(Supplier supplier, long expectedVersion);
}
