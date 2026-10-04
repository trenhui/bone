package com.bone.masterdata.application;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.AssignGovernanceRoleCommand;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.model.steward.StewardAssignment;
import com.bone.masterdata.domain.repository.StewardAssignmentRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 治理角色指派应用服务（G3，UC-T2）：OWNER / STEWARD / APPROVER。 */
@Service
@RequiredArgsConstructor
public class StewardApplicationService {

  private static final Set<String> VALID_ROLES = Set.of("OWNER", "STEWARD", "APPROVER");

  private final StewardAssignmentRepository stewardRepository;

  @Transactional
  public Long assign(AssignGovernanceRoleCommand cmd) {
    String role = cmd.getRoleType() == null ? "" : cmd.getRoleType().trim().toUpperCase();
    if (!VALID_ROLES.contains(role)) {
      throw MasterDataErrors.of(MasterDataErrorCodes.STEWARD_ROLE_INVALID, "非法角色: " + role);
    }
    if (stewardRepository.countByEntityAccountAndRole(
            cmd.getMasterDataEntityId(), cmd.getAccountId(), role)
        > 0) {
      throw MasterDataErrors.of(MasterDataErrorCodes.STEWARD_DUPLICATE, "该账号已承担此角色");
    }
    return stewardRepository.insert(
        StewardAssignment.assign(
            DistributedIdGenerator.generateLongId(),
            cmd.getMasterDataEntityId(),
            cmd.getAccountId(),
            role));
  }

  /**
   * 撤销指派（软删）。
   *
   * <p>之所以是「先 update 再 deleteById」两步：SDK 的 {@code deleteById} 只发 {@code SET deleted=1}，不会写 {@code
   * updated_at}（已核 BaseRepository#deleteById 与 SqlExecutor）。
   * 若直接软删，撤销时间将永远缺失，治理审计无法回答"该角色何时被撤"。故先由领域行为 {@link StewardAssignment#revoke()} 落下撤销时刻并
   * update，再软删。
   *
   * @param id 指派 ID
   * @throws com.bone.core.exception.BizException 指派不存在（含已被软删）时抛出，错误码 {@code
   *     MD_STEWARD_ASSIGNMENT_NOT_FOUND}（HTTP 404）
   */
  @Transactional
  public void unassign(Long id) {
    StewardAssignment assignment = stewardRepository.findById(id);
    if (assignment == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.STEWARD_ASSIGNMENT_NOT_FOUND, "指派不存在: " + id);
    }
    assignment.revoke();
    stewardRepository.update(assignment);
    stewardRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<StewardAssignment> byEntity(Long masterDataEntityId) {
    return stewardRepository.findByEntityId(masterDataEntityId);
  }
}
