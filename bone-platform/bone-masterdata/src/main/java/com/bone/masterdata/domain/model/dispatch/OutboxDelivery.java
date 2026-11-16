package com.bone.masterdata.domain.model.dispatch;

import com.bone.core.exception.DomainException;
import java.time.Instant;

/**
 * Outbox 投递单：在事件信封之外持有<b>投递状态机</b>（PENDING → DELIVERED / DEAD）与重试计数。
 *
 * <p><b>为什么要独立状态</b>：事件信封是不可变的事实（发什么），投递单是可变的投递过程（发了几次、成功没有）。 把状态塞进信封会让「同一事件被不同消费侧重放」互相污染；而「无状态中继 +
 * 只按列表重发」会造成 重复投影（N7）且失败次数不可查、死信不可追。
 *
 * <p><b>有界重试</b>：达到 {@code maxAttempts} 转 DEAD 并保留原因，绝不无限重试、绝不静默丢弃。
 */
public final class OutboxDelivery {

  public enum State {
    PENDING,
    DELIVERED,
    DEAD
  }

  private final MasterDataRecordDispatchedEvent event;
  private final int attempts;
  private final State state;
  private final String lastError;
  private final String deliveryNote;
  private final Instant updatedAt;

  private OutboxDelivery(
      MasterDataRecordDispatchedEvent event,
      int attempts,
      State state,
      String lastError,
      String deliveryNote,
      Instant updatedAt) {
    this.event = event;
    this.attempts = attempts;
    this.state = state;
    this.lastError = lastError;
    this.deliveryNote = deliveryNote;
    this.updatedAt = updatedAt;
  }

  public static OutboxDelivery pending(MasterDataRecordDispatchedEvent event, Instant at) {
    if (event == null || at == null) {
      throw new DomainException("投递单缺少事件或时间");
    }
    return new OutboxDelivery(event, 0, State.PENDING, null, null, at);
  }

  public OutboxDelivery delivered(String note, Instant at) {
    if (state != State.PENDING) {
      throw new DomainException("仅 PENDING 投递单可标记成功，当前: " + state);
    }
    return new OutboxDelivery(event, attempts + 1, State.DELIVERED, null, note, at);
  }

  /** 失败计数递增；达到上限即死信，仍保留事件与原因供人工重放。 */
  public OutboxDelivery failed(String reason, int maxAttempts, Instant at) {
    if (state != State.PENDING) {
      throw new DomainException("仅 PENDING 投递单可记录失败，当前: " + state);
    }
    if (maxAttempts < 1) {
      throw new DomainException("最大重试次数必须为正数");
    }
    int nextAttempts = attempts + 1;
    State next = nextAttempts >= maxAttempts ? State.DEAD : State.PENDING;
    return new OutboxDelivery(event, nextAttempts, next, reason, null, at);
  }

  public boolean isPending() {
    return state == State.PENDING;
  }

  public boolean isDead() {
    return state == State.DEAD;
  }

  public MasterDataRecordDispatchedEvent getEvent() {
    return event;
  }

  public int getAttempts() {
    return attempts;
  }

  public State getState() {
    return state;
  }

  public String getLastError() {
    return lastError;
  }

  public String getDeliveryNote() {
    return deliveryNote;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
