package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.changerequest.ChangeRequest;
import java.util.Optional;

/** 独立变更请求的出站持久化端口；生产实现由 L3 的 {@code mdm_change_request*} JDBC adapter 提供。 */
public interface ChangeRequestPort {

  /** 按租户 + CR 号取当前状态；跨租户查询返回 empty（fail-closed）。 */
  Optional<ChangeRequest> findById(long tenantId, String changeRequestId);

  /** 整笔 CR（含三段审批单与决策流水）原子落库。 */
  void save(ChangeRequest changeRequest);

  /** 同一租户/主体/目标版本已有已应用 CR 时返回 true，防止并行重复发布。 */
  boolean existsAppliedFor(long tenantId, String partyId, long targetVersion);
}
