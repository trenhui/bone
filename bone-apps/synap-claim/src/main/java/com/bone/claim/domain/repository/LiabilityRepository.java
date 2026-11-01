package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * A4 责任配置仓储。
 *
 * <p><b>写能力的有意演进</b>：早期约定"配置由外部导入、本域只读"，以 {@code §8.2「已生效版本不可改」} 在代码层表达（无 update
 * 入口即无从改起）。现补上运营侧写入口——{@code save} 由 {@code Repository} 基类提供，本接口只声明查询方法；读模型（理算）完全不受影响。版本化不变式由
 * {@code LiabilityConfigApplicationService} 保证：已生效版本不被原地修改，编辑走"新增版本"。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface LiabilityRepository extends Repository<AdjLiability, Long> {

  /** 按主键取（配置面编辑/发布用）。 */
  @TenantScope(TenantScopeMode.AUTO)
  AdjLiability findById(@Param("id") long id);

  /** 按计划取全部责任（含各版本，配置面树形展示用）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<AdjLiability> findByPlanId(@Param("planId") long planId);

  /** 按责任 UUID 取当前生效版本（同一 UUID 同时仅一个 ACTIVE）。 */
  @TenantScope(TenantScopeMode.AUTO)
  AdjLiability findActiveByUuid(@Param("liabilityUuid") String liabilityUuid);

  /** 按计划查当前生效责任（多责任时按 priority 升序取全部，由理算逐个匹配）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<AdjLiability> findActiveByPlanId(@Param("planId") long planId);

  /** 按责任 UUID 查指定版本（复算时取历史版本）。 */
  @TenantScope(TenantScopeMode.AUTO)
  AdjLiability findByUuidAndVersion(
      @Param("liabilityUuid") String liabilityUuid,
      @Param("liabilityVersion") String liabilityVersion);
}
