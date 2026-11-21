package com.bone.procurement.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.bone.core.domain.entity.Entity;
import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.model.capability.AiCapability;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.CapabilityState;
import com.bone.procurement.governance.domain.model.capability.GlobalKillSwitch;
import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import com.bone.procurement.governance.domain.model.capability.HumanFinalDecision;
import com.bone.procurement.governance.domain.model.capability.OwnerRef;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 治理域持久化红灯→转绿灯测试：能力/终决/总闸/追溯实体往返、重启读回、总闸即时生效、缺操作人 fail-closed、PII 不落库。
 *
 * <p>SDK Repository 用轻量 fake（Mockito + 条件过滤 Map）模拟，不引 Testcontainers、不连 MySQL。
 */
class GovernancePersistenceTest {

  private static final String ID_CARD = "110101199001011234";

  private final Map<Long, AiCapabilityEntity> capabilityDb = new LinkedHashMap<>();
  private final Map<Long, HumanDecisionEntity> decisionDb = new LinkedHashMap<>();
  private final Map<Long, GlobalKillSwitchEntity> killSwitchDb = new LinkedHashMap<>();
  private final Map<Long, GovernanceTraceEntity> traceDb = new LinkedHashMap<>();

  private final AiCapabilitySdkRepository capabilityRepo =
      fakeRepo(AiCapabilitySdkRepository.class, capabilityDb);
  private final HumanDecisionSdkRepository decisionRepo =
      fakeRepo(HumanDecisionSdkRepository.class, decisionDb);
  private final GlobalKillSwitchSdkRepository killSwitchRepo =
      fakeRepo(GlobalKillSwitchSdkRepository.class, killSwitchDb);
  private final GovernanceTraceSdkRepository traceRepo =
      fakeRepo(GovernanceTraceSdkRepository.class, traceDb);

  private final GovernanceActorPort actor = () -> "operator-1";

  @BeforeEach
  void setUpTenant() {
    TenantContext.setTenantId(1L);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.setTenantId((Long) null);
  }

  @Test
  @DisplayName("缺租户上下文：治理四端口均拒绝写入且无落库副作用")
  void missingTenantRejectedByAllGovernancePorts() {
    TenantContext.setTenantId((Long) null);
    AiCapability capability =
        AiCapability.register(new CapabilityCode("G0"), "合同审查", OwnerRef.of("owner-0", "责任人"));
    HumanFinalDecision decision =
        new HumanFinalDecision(
            "hfd-0",
            new CapabilityCode("G0"),
            "subject-0",
            "HIGH",
            0.8,
            "LOW",
            "reviewer-0",
            "reason",
            1L,
            true,
            false,
            Instant.now());

    assertThatThrownBy(() -> new CapabilityRegistryAdapter(capabilityRepo).save(capability))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("租户");
    assertThatThrownBy(() -> new HumanDecisionLedgerAdapter(decisionRepo).append(decision))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("租户");
    assertThatThrownBy(
            () ->
                new GlobalKillSwitchStoreAdapter(killSwitchRepo, actor)
                    .save(GlobalKillSwitch.standby()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("租户");
    assertThatThrownBy(
            () ->
                new GovernanceTraceAdapter(traceRepo)
                    .append(GovernanceTraceEntry.of("G0", "ENABLED", "operator=operator-0")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("租户");

    assertThat(capabilityDb).isEmpty();
    assertThat(decisionDb).isEmpty();
    assertThat(killSwitchDb).isEmpty();
    assertThat(traceDb).isEmpty();
  }

  @Test
  @DisplayName("能力登记往返映射 + 重启读回：状态/责任人/追溯链完整恢复")
  void capabilityRoundTrip() {
    AiCapability capability =
        AiCapability.register(new CapabilityCode("G7"), "合同审查", OwnerRef.of("owner-1", "张责任人"));
    capability.enable("operator-1", "显式启用");
    new CapabilityRegistryAdapter(capabilityRepo).save(capability);

    AiCapability restored =
        new CapabilityRegistryAdapter(capabilityRepo).find(new CapabilityCode("G7")).orElseThrow();

    assertThat(restored.code().value()).isEqualTo("G7");
    assertThat(restored.displayName()).isEqualTo("合同审查");
    assertThat(restored.state()).isEqualTo(CapabilityState.AVAILABLE);
    assertThat(restored.owner().userId()).isEqualTo("owner-1");
    assertThat(restored.trace()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(new CapabilityRegistryAdapter(capabilityRepo).listAll()).hasSize(1);
    assertThat(new CapabilityRegistryAdapter(capabilityRepo).exists(new CapabilityCode("G7")))
        .isTrue();
  }

  @Test
  @DisplayName("人工终决往返映射：结论/复核人/耗时恢复，PII 理由脱敏不落库")
  void humanDecisionRoundTripAndPiiRedaction() {
    HumanFinalDecision decision =
        new HumanFinalDecision(
            "hfd-1",
            new CapabilityCode("G7"),
            "subject-1",
            "HIGH",
            0.8,
            "LOW，复核人身份证 " + ID_CARD,
            "reviewer-1",
            "复核人身份证 " + ID_CARD,
            1200L,
            true,
            true,
            Instant.now());
    new HumanDecisionLedgerAdapter(decisionRepo).append(decision);

    HumanDecisionEntity entity = decisionDb.values().iterator().next();
    assertThat(entity.getReason()).doesNotContain(ID_CARD);
    assertThat(entity.getFinalConclusion()).doesNotContain(ID_CARD);

    HumanDecisionLedgerAdapter restarted = new HumanDecisionLedgerAdapter(decisionRepo);
    HumanFinalDecision restored = restarted.find("hfd-1").orElseThrow();
    assertThat(restored.finalConclusion()).doesNotContain(ID_CARD);
    assertThat(restored.reviewer()).isEqualTo("reviewer-1");
    assertThat(restored.reviewDurationMs()).isEqualTo(1200L);
    assertThat(restored.isOverride()).isTrue();
    assertThat(restarted.countOverrides(new CapabilityCode("G7"))).isEqualTo(1);
    assertThat(restarted.findBySubject("subject-1")).hasSize(1);
  }

  @Test
  @DisplayName("总闸切换后立即生效：每次裁决读最新持久化行，无缓存")
  void killSwitchTakesEffectImmediately() {
    GlobalKillSwitchStoreAdapter writer = new GlobalKillSwitchStoreAdapter(killSwitchRepo, actor);
    GlobalKillSwitch ks = GlobalKillSwitch.standby();
    ks.engage("operator-1", "停机演练", "drill-1");
    writer.save(ks);

    GlobalKillSwitchStoreAdapter reader = new GlobalKillSwitchStoreAdapter(killSwitchRepo, actor);
    assertThat(reader.current().mode()).isEqualTo(GlobalKillSwitch.Mode.ENGAGED);
    assertThat(reader.current().allowsAi()).isFalse();

    GlobalKillSwitch engaged = reader.current();
    engaged.disengage("operator-1", "恢复");
    writer.save(engaged);

    assertThat(reader.current().mode()).isEqualTo(GlobalKillSwitch.Mode.DISENGAGED);
    assertThat(reader.current().allowsAi()).isTrue();
  }

  @Test
  @DisplayName("缺登录态操作人写入总闸 fail-closed")
  void killSwitchWriteWithoutActorRejected() {
    GlobalKillSwitch ks = GlobalKillSwitch.standby();
    ks.engage("operator-1", "停机演练", "drill-1");
    GlobalKillSwitchStoreAdapter anonymous =
        new GlobalKillSwitchStoreAdapter(killSwitchRepo, () -> null);

    assertThatThrownBy(() -> anonymous.save(ks))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("操作人");
    assertThat(killSwitchDb).isEmpty();
  }

  @Test
  @DisplayName("治理追溯链往返映射：按能力码检索")
  void governanceTraceRoundTrip() {
    GovernanceTraceAdapter adapter = new GovernanceTraceAdapter(traceRepo);
    adapter.append(GovernanceTraceEntry.of("G7", "ENABLED", "operator=operator-1"));
    adapter.append(GovernanceTraceEntry.of("G8", "REGISTERED", "operator=operator-2"));

    List<GovernanceTraceEntry> entries =
        new GovernanceTraceAdapter(traceRepo).listByCapability("G7");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).eventType()).isEqualTo("ENABLED");
    assertThat(entries.get(0).detail()).contains("operator=operator-1");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T extends Entity<Long>, R extends Repository<T, Long>> R fakeRepo(
      Class<R> type, Map<Long, T> data) {
    R repository = mock(type);
    doAnswer(
            inv -> {
              T entity = inv.getArgument(0);
              if (entity.getId() == null) entity.setId((long) (data.size() + 1));
              data.put(entity.getId(), entity);
              return entity.getId();
            })
        .when(repository)
        .save(any());
    doAnswer(inv -> query(data, inv.getArgument(0))).when(repository).findByCriteria(any());
    doAnswer(inv -> query(data, inv.getArgument(0)).stream().findFirst().orElse(null))
        .when(repository)
        .findOneByCriteria(any());
    doAnswer(inv -> (long) query(data, inv.getArgument(0)).size())
        .when(repository)
        .countByCriteria(any());
    return repository;
  }

  private static <T> List<T> query(Map<Long, T> data, Criteria<T> criteria) {
    List<Condition> conditions = criteria.getMainConditions();
    return data.values().stream()
        .filter(
            entity ->
                conditions.stream()
                    .allMatch(
                        c -> Objects.equals(readField(entity, c.getFieldName()), c.getValues()[0])))
        .toList();
  }

  private static Object readField(Object bean, String name) {
    try {
      Class<?> type = bean.getClass();
      while (type != null) {
        try {
          Field field = type.getDeclaredField(name);
          field.setAccessible(true);
          return field.get(bean);
        } catch (NoSuchFieldException ignored) {
          type = type.getSuperclass();
        }
      }
      throw new IllegalArgumentException("字段不存在: " + name);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
