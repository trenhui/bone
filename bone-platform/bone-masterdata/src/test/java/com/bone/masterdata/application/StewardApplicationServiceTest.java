package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.masterdata.application.command.AssignGovernanceRoleCommand;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.domain.model.steward.StewardAssignment;
import com.bone.masterdata.domain.repository.StewardAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** {@link StewardApplicationService} 单测：重点覆盖 unassign 的「先记撤销时刻再软删」两段语义。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StewardApplicationServiceTest {

  @Mock private StewardAssignmentRepository stewardRepository;
  @InjectMocks private StewardApplicationService service;

  @Test
  void unassignRevokesThenSoftDeletes() {
    StewardAssignment assignment = StewardAssignment.assign(1L, 10L, 20L, "STEWARD");
    when(stewardRepository.findById(1L)).thenReturn(assignment);

    service.unassign(1L);

    // 顺序不能反：先 update 落下撤销时刻，再 deleteById 软删；
    // 若只软删，updated_at 永远缺失（SDK 的 deleteById 只发 SET deleted=1）
    verify(stewardRepository).update(assignment);
    verify(stewardRepository).deleteById(1L);
  }

  @Test
  void unassignRejectsUnknownIdWithoutTouchingRepository() {
    when(stewardRepository.findById(99L)).thenReturn(null);

    BizException ex = assertThrows(BizException.class, () -> service.unassign(99L));

    assertEquals(MasterDataErrorCodes.STEWARD_ASSIGNMENT_NOT_FOUND, ex.getErrorCode());
    // 不存在时不得发出任何写操作——否则会把"撤销不存在的指派"变成静默成功
    verify(stewardRepository, never()).update(any(StewardAssignment.class));
    verify(stewardRepository, never()).deleteById(any());
  }

  @Test
  void assignRejectsDuplicateRole() {
    when(stewardRepository.countByEntityAccountAndRole(10L, 20L, "OWNER")).thenReturn(1L);

    AssignGovernanceRoleCommand cmd = new AssignGovernanceRoleCommand();
    cmd.setMasterDataEntityId(10L);
    cmd.setAccountId(20L);
    cmd.setRoleType("OWNER");

    BizException ex = assertThrows(BizException.class, () -> service.assign(cmd));

    assertEquals(MasterDataErrorCodes.STEWARD_DUPLICATE, ex.getErrorCode());
  }

  @Test
  void assignRejectsInvalidRole() {
    AssignGovernanceRoleCommand cmd = new AssignGovernanceRoleCommand();
    cmd.setMasterDataEntityId(10L);
    cmd.setAccountId(20L);
    cmd.setRoleType("ROOT");

    BizException ex = assertThrows(BizException.class, () -> service.assign(cmd));

    assertEquals(MasterDataErrorCodes.STEWARD_ROLE_INVALID, ex.getErrorCode());
  }
}
