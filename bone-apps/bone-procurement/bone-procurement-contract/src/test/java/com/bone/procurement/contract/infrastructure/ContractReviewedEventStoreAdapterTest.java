package com.bone.procurement.contract.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.contract.domain.gateway.ContractReviewedEventStore;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReviewedConclusion;
import com.bone.procurement.contract.domain.model.review.ContractReviewedDlqEntry;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.CrossTenantAccessException;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 事件闭环持久化红灯→转绿灯测试：tenant 锚定定位、Inbox 条件插入唯一键冲突、重启读回、DLQ 读写。
 *
 * <p>SDK Repository 用轻量 fake（Mockito + 条件过滤 Map）模拟，不连 MySQL。
 */
class ContractReviewedEventStoreAdapterTest {

  private final Map<Long, ContractReviewedOutboxEntity> outbox = new LinkedHashMap<>();
  private final Map<Long, ContractReviewedInboxEntity> inbox = new LinkedHashMap<>();
  private final Map<Long, ContractReviewedDlqEntity> dlq = new LinkedHashMap<>();
  private final ContractReviewedEventStore store =
      new ContractReviewedEventStoreAdapter(
          fakeRepo(outbox, ContractReviewedOutboxSdkRepository.class),
          fakeRepo(inbox, ContractReviewedInboxSdkRepository.class),
          fakeRepo(dlq, ContractReviewedDlqSdkRepository.class));

  @Test
  @DisplayName("tenant 锚定定位：t-1 发布只出现在 t-1 待投递队列，t-2 查不到")
  void tenantAnchoredOutboxScan() {
    store.publishOutbox("t-1", event("rv-1"));

    assertThat(store.scanPending("t-1")).hasSize(1);
    assertThat(store.scanPending("t-2")).isEmpty();
    assertThat(store.findOutbox("t-1", "rv-1")).isPresent();
    assertThat(store.findOutbox("t-2", "rv-1")).isEmpty();
    assertThat(outbox.values().iterator().next().getPayloadJson())
        .contains("\"reviewId\":\"rv-1\"")
        .doesNotContain("output");
  }

  @Test
  @DisplayName("同租户重复发布 Outbox 幂等，不产生第二条待投递")
  void duplicateOutboxPublishDeduped() {
    store.publishOutbox("t-1", event("rv-dup"));
    store.publishOutbox("t-1", event("rv-dup"));

    assertThat(outbox).hasSize(1);
    assertThat(store.scanPending("t-1")).hasSize(1);
  }

  @Test
  @DisplayName("跨租户覆写 Outbox 被拒，不改原记录")
  void crossTenantOutboxRejected() {
    store.publishOutbox("t-1", event("rv-ct"));

    assertThatThrownBy(() -> store.publishOutbox("t-2", event("rv-ct")))
        .isInstanceOf(CrossTenantAccessException.class);
    assertThat(outbox).hasSize(1);
    assertThat(store.findOutbox("t-1", "rv-ct")).isPresent();
  }

  @Test
  @DisplayName("Inbox 条件插入唯一键冲突：第二次 claim 返回 false，只留一条结论")
  void inboxConditionalInsertDeduped() {
    ContractReviewedConclusion conclusion = conclusion("t-1", "rv-in");

    assertThat(store.claimInbox(conclusion)).isTrue();
    assertThat(store.claimInbox(conclusion)).as("唯一键冲突即重复投递").isFalse();
    assertThat(inbox).hasSize(1);
  }

  @Test
  @DisplayName("持久化读回：换一个适配器实例（模拟重启）仍能读回结论与租户")
  void persistenceReadBackAfterRestart() {
    store.claimInbox(conclusion("t-42", "rv-restart"));

    ContractReviewedEventStore restarted =
        new ContractReviewedEventStoreAdapter(
            fakeRepo(outbox, ContractReviewedOutboxSdkRepository.class),
            fakeRepo(inbox, ContractReviewedInboxSdkRepository.class),
            fakeRepo(dlq, ContractReviewedDlqSdkRepository.class));
    Optional<ContractReviewedConclusion> reread = restarted.findInbox("t-42", "rv-restart");

    assertThat(reread).isPresent();
    ContractReviewedConclusion c = reread.orElseThrow();
    assertThat(c.tenantId()).isEqualTo("t-42");
    assertThat(c.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(c.aiAvailable()).isEqualTo(AiParticipation.AVAILABLE);
    assertThat(c.reviewSnapshotRef()).isEqualTo("snap://override/rv-restart");
    assertThat(c.quarantined()).isFalse();
    assertThat(restarted.findInbox("t-other", "rv-restart")).isEmpty();
  }

  @Test
  @DisplayName("DLQ 写入/读回/标记已解决：按 review_id 定位且 tenant 锚定")
  void dlqWriteReadResolve() {
    store.appendDlq("t-1", "rv-dlq", "消费失败：追溯写入异常", "outbox://t-1/rv-dlq");

    ContractReviewedDlqEntry entry = store.findDlq("t-1", "rv-dlq").orElseThrow();
    assertThat(entry.reason()).contains("追溯写入异常");
    assertThat(entry.resolved()).isFalse();
    assertThat(store.findDlq("t-2", "rv-dlq")).isEmpty();

    store.resolveDlq("t-1", "rv-dlq");
    assertThat(store.findDlq("t-1", "rv-dlq").orElseThrow().resolved()).isTrue();
    assertThat(dlq).hasSize(1);
  }

  private static ContractReviewedEvent event(String reviewId) {
    return new ContractReviewedEvent(
        reviewId,
        "CT-" + reviewId,
        RiskLevel.HIGH,
        "hash",
        "snap://override/" + reviewId,
        AiParticipation.AVAILABLE,
        "m1");
  }

  private static ContractReviewedConclusion conclusion(String tenantId, String reviewId) {
    return new ContractReviewedConclusion(
        tenantId,
        reviewId,
        RiskLevel.HIGH,
        AiParticipation.AVAILABLE,
        "snap://override/" + reviewId,
        false);
  }

  @SuppressWarnings("unchecked")
  private static <T extends AbstractEntity<Long>, R extends Repository<T, Long>> R fakeRepo(
      Map<Long, T> data, Class<R> repoType) {
    R repository = mock(repoType);
    doAnswer(
            inv -> {
              T entity = inv.getArgument(0);
              if (entity.getId() != null && data.containsKey(entity.getId())) {
                data.put(entity.getId(), entity);
                return entity.getId();
              }
              boolean duplicate =
                  data.values().stream()
                      .anyMatch(
                          e ->
                              Objects.equals(
                                      readField(e, "tenantId"), readField(entity, "tenantId"))
                                  && Objects.equals(
                                      readField(e, "reviewId"), readField(entity, "reviewId")));
              if (duplicate) {
                throw new IllegalStateException(
                    "UNIQUE constraint violation (tenant_id, review_id)");
              }
              if (entity.getId() == null) {
                setField(entity, "id", (long) (data.size() + 1));
              }
              Date now = new Date();
              if (readField(entity, "createdAt") == null) {
                setField(entity, "createdAt", now);
              }
              setField(entity, "updatedAt", now);
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
      return null;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void setField(Object bean, String name, Object value) {
    try {
      Class<?> type = bean.getClass();
      while (type != null) {
        try {
          Field field = type.getDeclaredField(name);
          field.setAccessible(true);
          field.set(bean, value);
          return;
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
