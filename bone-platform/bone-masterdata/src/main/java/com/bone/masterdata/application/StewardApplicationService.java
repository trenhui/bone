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

  @Transactional
  public void unassign(Long id) {
    stewardRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<StewardAssignment> byEntity(Long masterDataEntityId) {
    return stewardRepository.findByEntityId(masterDataEntityId);
  }
}
