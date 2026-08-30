package com.bone.core.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.DomainEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link DomainEventPublisher} 测试：重点覆盖 {@code publishFrom} default 方法的「发布即清空」契约。
 *
 * <p>该方法是样板工程「保存后发布领域事件」的标准写法，一旦破坏（忘记清空）会导致事件重复投递， 故在此固化行为。
 */
class DomainEventPublisherTest {

  /** 记录型实现：只收集事件，不覆写 publishFrom（走 default 实现以验证其行为）。 */
  private static final class RecordingPublisher implements DomainEventPublisher {
    final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      published.add(event);
    }

    @Override
    public void publishAll(Collection<? extends DomainEvent> events) {
      published.addAll(events);
    }
  }

  /** 测试用聚合：暴露 protected 的 addDomainEvent 供测试挂载事件。 */
  private static final class TestAggregate extends AggregateRoot<Long> {
    void record(DomainEvent event) {
      addDomainEvent(event);
    }
  }

  private record TestEvent(String name) implements DomainEvent {}

  @Test
  void publishFrom_publishesThenClears() {
    RecordingPublisher publisher = new RecordingPublisher();
    TestAggregate aggregate = new TestAggregate();
    aggregate.record(new TestEvent("created"));
    aggregate.record(new TestEvent("paid"));
    assertThat(aggregate.getDomainEvents()).hasSize(2);

    publisher.publishFrom(aggregate);

    assertThat(publisher.published)
        .containsExactly(new TestEvent("created"), new TestEvent("paid"));
    // 核心契约：发布后必须清空，否则同一事件会被重复投递
    assertThat(aggregate.getDomainEvents()).isEmpty();
  }

  @Test
  void publishFrom_calledTwiceDoesNotDuplicate() {
    RecordingPublisher publisher = new RecordingPublisher();
    TestAggregate aggregate = new TestAggregate();
    aggregate.record(new TestEvent("created"));

    publisher.publishFrom(aggregate);
    publisher.publishFrom(aggregate);

    assertThat(publisher.published).hasSize(1);
  }

  @Test
  void publishFrom_nullAggregateIsNoop() {
    RecordingPublisher publisher = new RecordingPublisher();

    publisher.publishFrom(null);

    assertThat(publisher.published).isEmpty();
  }

  @Test
  void publishFrom_aggregateWithoutEventsIsNoop() {
    RecordingPublisher publisher = new RecordingPublisher();

    publisher.publishFrom(new TestAggregate());

    assertThat(publisher.published).isEmpty();
  }
}
