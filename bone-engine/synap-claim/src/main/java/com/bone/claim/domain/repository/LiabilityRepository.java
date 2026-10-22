package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * A4 责任配置仓储（只读查询 + 版本锁定）。
 *
 * <p>不定义写方法：配置由运营/迁移导入，业务运行时只读——这也是 §8.2「已生效版本不可改」在 代码层面的表达（没有 update 入口即无从改起）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface LiabilityRepository extends Repository<AdjLiability, Long> {

  /** 按计划查当前生效责任（多责任时按 priority 升序取全部，由理算逐个匹配）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<AdjLiability> findActiveByPlanId(@Param("planId") long planId);

  /** 按责任 UUID 查指定版本（复算时取历史版本）。 */
  @TenantScope(TenantScopeMode.AUTO)
  AdjLiability findByUuidAndVersion(
      @Param("liabilityUuid") String liabilityUuid,
      @Param("liabilityVersion") String liabilityVersion);
}
