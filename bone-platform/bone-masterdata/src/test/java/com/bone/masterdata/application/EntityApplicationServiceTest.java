package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.gateway.MetaEntityCatalogPort;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityName;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityStatus;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link EntityApplicationService} 评审流单测（UC-MD-T7）。
 *
 * <p>覆盖三条业界 MDM 治理不变量：① L2/L3 实体未审批不得发布（工作流门禁）；② 审批人不得为提交人（SoD）； ③ 驳回必须留痕（原因必填）。L1
 * 直发路径同时回归，确保门禁没有误伤轻量治理等级。
 */
@ExtendWith(MockitoExtension.class)
class EntityApplicationServiceTest {

  private static final long ENTITY_ID = 42L;

  @Mock private MasterDataEntityRepository entityRepository;
  @Mock private MasterDataFieldRepository fieldRepository;
  @Mock private MetaEntityCatalogPort metaEntityCatalogPort;
  @Mock private MasterdataDomainEventPublisher domainEventPublisher;
  @Mock private CurrentUserPort currentUser;

  @InjectMocks private EntityApplicationService service;

  private MasterDataEntity newEntity(String tier) {
    MasterDataEntity entity =
        MasterDataEntity.create(
            ENTITY_ID, null, MasterDataEntityName.of("客户主数据"), "客户域", "customer");
    entity.changeGovernanceTier(tier);
    return entity;
  }

  private void stubFind(MasterDataEntity entity) {
    when(entityRepository.findById(ENTITY_ID)).thenReturn(entity);
  }

  @Test
  void submitForReviewRecordsSubmitterAndTransitions() {
    MasterDataEntity entity = newEntity("L2");
    stubFind(entity);
    when(currentUser.requireUserId()).thenReturn(1001L);

    service.submitForReview(ENTITY_ID);

    assertEquals(MasterDataEntityStatus.PENDING_REVIEW, entity.getStatus());
    assertEquals(1001L, entity.getSubmittedBy());
  }

  @Test
  void approveRejectsSelfApproval() {
    MasterDataEntity entity = newEntity("L2");
    entity.submitForReview();
    entity.markSubmittedBy(1001L);
    stubFind(entity);
    // 同一人既提交又审批：SoD 硬校验必须拦截（否则评审流只是摆设）
    when(currentUser.requireUserId()).thenReturn(1001L);

    BizException ex = assertThrows(BizException.class, () -> service.approve(ENTITY_ID, "同意"));

    assertEquals(MasterDataErrorCodes.SOD_VIOLATION, ex.getErrorCode());
    assertEquals(MasterDataEntityStatus.PENDING_REVIEW, entity.getStatus());
  }

  @Test
  void approveTransitionsToApprovedWithApprover() {
    MasterDataEntity entity = newEntity("L2");
    entity.submitForReview();
    entity.markSubmittedBy(1001L);
    stubFind(entity);
    when(currentUser.requireUserId()).thenReturn(2002L);

    service.approve(ENTITY_ID, "同意");

    assertEquals(MasterDataEntityStatus.APPROVED, entity.getStatus());
    assertEquals(2002L, entity.getApprovedBy());
  }

  @Test
  void rejectRequiresReason() {
    MasterDataEntity entity = newEntity("L2");
    entity.submitForReview();
    entity.markSubmittedBy(1001L);
    stubFind(entity);

    // 空原因驳回：提交人无从改起，必须拦截（错误码而非裸 DomainException）。
    // 注意：原因校验在 requireUserId 之前，故此处无需 stub 当前用户（严格 stubbing 会报 UnnecessaryStubbing）。
    BizException ex = assertThrows(BizException.class, () -> service.reject(ENTITY_ID, "  "));

    assertEquals(MasterDataErrorCodes.REJECT_REASON_REQUIRED, ex.getErrorCode());
    assertEquals(MasterDataEntityStatus.PENDING_REVIEW, entity.getStatus());
  }

  @Test
  void rejectReturnsToDraftWithReason() {
    MasterDataEntity entity = newEntity("L2");
    entity.submitForReview();
    entity.markSubmittedBy(1001L);
    stubFind(entity);
    when(currentUser.requireUserId()).thenReturn(2002L);

    service.reject(ENTITY_ID, "字段命名不符合数据标准");

    assertEquals(MasterDataEntityStatus.DRAFT, entity.getStatus());
    assertEquals("字段命名不符合数据标准", entity.getRejectReason());
  }

  @Test
  void rejectAlsoEnforcesSoD() {
    MasterDataEntity entity = newEntity("L2");
    entity.submitForReview();
    entity.markSubmittedBy(1001L);
    stubFind(entity);
    when(currentUser.requireUserId()).thenReturn(1001L);

    // 驳回与通过同属审批动作，同样受 SoD 约束（只拦通过不拦驳回=半个门禁）
    BizException ex = assertThrows(BizException.class, () -> service.reject(ENTITY_ID, "自己驳自己"));

    assertEquals(MasterDataErrorCodes.SOD_VIOLATION, ex.getErrorCode());
  }

  @Test
  void publishBlockedForWorkflowEntityUntilApproved() {
    MasterDataEntity entity = newEntity("L2");
    stubFind(entity);

    // 草稿直接发布：L2 工作流门禁拦截（核心不变量——否则治理等级对实体定义空转）
    BizException ex = assertThrows(BizException.class, () -> service.publish(ENTITY_ID));

    assertEquals(MasterDataErrorCodes.WORKFLOW_APPROVAL_REQUIRED, ex.getErrorCode());
    assertEquals(MasterDataEntityStatus.DRAFT, entity.getStatus());

    // 待评审态同样拦截（提交人不能自审自发布）
    entity.submitForReview();
    assertThrows(BizException.class, () -> service.publish(ENTITY_ID));

    // 走完评审流后方可发布
    entity.approve();
    service.publish(ENTITY_ID);
    assertEquals(MasterDataEntityStatus.PUBLISHED, entity.getStatus());
  }

  @Test
  void publishAllowedDirectlyForL1Entity() {
    MasterDataEntity entity = newEntity("L1");
    stubFind(entity);

    // L1 无审批流：草稿直发不受门禁影响（回归防护：门禁不得误伤轻量治理等级）
    service.publish(ENTITY_ID);

    assertEquals(MasterDataEntityStatus.PUBLISHED, entity.getStatus());
  }
}
