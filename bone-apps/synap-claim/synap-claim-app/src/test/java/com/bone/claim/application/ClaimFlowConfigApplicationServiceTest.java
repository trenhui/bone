package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.dto.request.FlowConfigReq;
import com.bone.claim.application.port.out.DecisionFlowConfigPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.config.ClaimFlowConfig;
import com.bone.claim.domain.model.config.ClaimFlowConfigChange;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.repository.ClaimFlowConfigRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** M5a 写侧钉子：两个分支都必须发事件（删掉 publishChanged 即转红），白名单外节点写入期拒绝。 */
class ClaimFlowConfigApplicationServiceTest {

  private static final long TENANT = 42L;

  private static final class Fixture {
    final ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class);
    final TenantPort tenant = mock(TenantPort.class);
    final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
    final ClaimFlowConfigApplicationService service =
        new ClaimFlowConfigApplicationService(repo, tenant, new ObjectMapper(), publisher);

    Fixture() {
      when(tenant.currentTenantId()).thenReturn(TENANT);
    }
  }

  private static ClaimFlowConfig existing() {
    return ClaimFlowConfig.create(
        TENANT, DecisionFlowConfigPort.FLOW_KEY, 1, "{\"skipNodes\":[]}", Instant.now());
  }

  @Test
  @DisplayName("新建分支：save 后发 CREATED 事件（E-5.4）")
  void createPublishesEvent() {
    Fixture f = new Fixture();
    when(f.repo.findByFlowKey(TENANT, DecisionFlowConfigPort.FLOW_KEY)).thenReturn(null);

    f.service.save(Set.of("AiAssess"));

    verify(f.repo).save(any(ClaimFlowConfig.class));
    ArgumentCaptor<ClaimFlowConfigChange> captor =
        ArgumentCaptor.forClass(ClaimFlowConfigChange.class);
    verify(f.publisher).publishFrom(captor.capture());
    assertThat(captor.getValue().event().changeType()).isEqualTo("CREATED");
    assertThat(captor.getValue().event().tenantId()).isEqualTo(TENANT);
    assertThat(captor.getValue().event().flowKey()).isEqualTo(DecisionFlowConfigPort.FLOW_KEY);
  }

  @Test
  @DisplayName("覆写分支：save 后发 OVERWRITTEN 事件，且 configVersion 递增")
  void overwritePublishesEvent() {
    Fixture f = new Fixture();
    when(f.repo.findByFlowKey(TENANT, DecisionFlowConfigPort.FLOW_KEY)).thenReturn(existing());

    var view = f.service.save(Set.of());

    assertThat(view.configVersion()).isEqualTo(2);
    ArgumentCaptor<ClaimFlowConfigChange> captor =
        ArgumentCaptor.forClass(ClaimFlowConfigChange.class);
    verify(f.publisher).publishFrom(captor.capture());
    assertThat(captor.getValue().event().changeType()).isEqualTo("OVERWRITTEN");
  }

  @Test
  @DisplayName("白名单外节点在写入期拒绝，且不落库、不发事件")
  void illegalNodeIsRejectedBeforeSave() {
    Fixture f = new Fixture();

    assertThatThrownBy(() -> f.service.save(Set.of("Adjust")))
        .hasMessageContaining("SC_CLAIM_FLOW_CONFIG_INVALID");
    verify(f.repo, never()).save(any());
    verify(f.publisher, never()).publishFrom(any());
  }

  @Test
  @DisplayName("节点存在性：执行图里不存在的节点写入期拒绝，不落库、不发事件")
  void unknownNodeIsRejectedBeforeSave() {
    Fixture f = new Fixture();

    assertThatThrownBy(() -> f.service.save(Set.of("FraudScreen")))
        .hasMessageContaining(ClaimErrorCodes.CLAIM_FLOW_CONFIG_INVALID);
    verify(f.repo, never()).save(any());
    verify(f.publisher, never()).publishFrom(any());
  }

  @Test
  @DisplayName("白名单外节点：四个必需节点逐个拒绝，且与 AiAssess 混合提交也整批拒绝（不部分写入）")
  void nonWhitelistedNodesAreRejectedBeforeSave() {
    for (String node :
        new String[] {
          DecisionGraph.EVIDENCE_LOAD,
          DecisionGraph.RULE_CHECK,
          DecisionGraph.ADJUST,
          DecisionGraph.ARBITRATE
        }) {
      Fixture f = new Fixture();
      assertThatThrownBy(() -> f.service.save(Set.of(node)))
          .hasMessageContaining(ClaimErrorCodes.CLAIM_FLOW_CONFIG_INVALID);
      verify(f.repo, never()).save(any());
      verify(f.publisher, never()).publishFrom(any());
    }

    Fixture mixed = new Fixture();
    assertThatThrownBy(
            () -> mixed.service.save(Set.of(DecisionGraph.AI_ASSESS, DecisionGraph.ADJUST)))
        .hasMessageContaining(ClaimErrorCodes.CLAIM_FLOW_CONFIG_INVALID);
    verify(mixed.repo, never()).save(any());
    verify(mixed.publisher, never()).publishFrom(any());
  }

  @Test
  @DisplayName("skipNodes 非法：空白/不存在的节点名写入期拒绝，不落库、不发事件")
  void blankAndUnknownSkipNodesAreRejectedBeforeSave() {
    Fixture blank = new Fixture();
    assertThatThrownBy(() -> blank.service.save(Set.of("")))
        .hasMessageContaining(ClaimErrorCodes.CLAIM_FLOW_CONFIG_INVALID);
    verify(blank.repo, never()).save(any());
    verify(blank.publisher, never()).publishFrom(any());
  }

  @Test
  @DisplayName("skipNodes 非法（null）：入站 DTO @NotNull 拒绝，避免「没传」与「显式清空」混淆")
  void flowConfigReqRejectsNullSkipNodes() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      assertThat(validator.validate(new FlowConfigReq(null)))
          .anySatisfy(
              violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("skipNodes");
                assertThat(violation.getMessage()).contains("skipNodes 不能为 null");
              });
    }
  }

  @Test
  @DisplayName("回滚到空集：config_json 写回 skipNodes=[]，configVersion 递增并留痕 OVERWRITTEN")
  void rollbackToEmptyRestoresCanonicalConfigVersion() {
    Fixture f = new Fixture();
    ClaimFlowConfig published =
        ClaimFlowConfig.create(
            TENANT,
            DecisionFlowConfigPort.FLOW_KEY,
            7,
            "{\"skipNodes\":[\"AiAssess\"]}",
            Instant.now());
    when(f.repo.findByFlowKey(TENANT, DecisionFlowConfigPort.FLOW_KEY)).thenReturn(published);

    var view = f.service.save(Set.of());

    assertThat(view.skipNodes()).isEmpty();
    assertThat(view.configVersion()).isEqualTo(8);
    ArgumentCaptor<ClaimFlowConfig> saved = ArgumentCaptor.forClass(ClaimFlowConfig.class);
    verify(f.repo).save(saved.capture());
    assertThat(saved.getValue().getConfigJson()).contains("\"skipNodes\":[]");
    ArgumentCaptor<ClaimFlowConfigChange> changed =
        ArgumentCaptor.forClass(ClaimFlowConfigChange.class);
    verify(f.publisher).publishFrom(changed.capture());
    assertThat(changed.getValue().event().changeType()).isEqualTo("OVERWRITTEN");
    assertThat(changed.getValue().event().configVersion()).isEqualTo(8);
  }
}
