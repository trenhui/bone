package com.bone.blueprint.application.util;

import com.bone.core.domain.DomainEvent;
import java.util.List;

/**
 * 聚合「已注册领域事件」的读取辅助（application 层共用）。
 *
 * <p><b>为何需要</b>：Outbox 集成事件必须在业务写事务<strong>内</strong>落库，而 {@code
 * DomainEventPublisher#publishFrom(aggregate)} 发布后<strong>会清空</strong>聚合的事件列表，故「先取出待发事件、再
 * publishFrom」是固定顺序。这段「按类型取首个事件」原先在 {@code PaymentApplicationService} 与 {@code
 * PaymentSucceededEventHandler} 各写了一份，收敛到此处以免两处各自演进。
 *
 * <p><b>为什么返回 {@code null} 而不是 {@code Optional}</b>：调用点要区分的是「本次操作<strong>没有</strong>产生该事件」
 * 这一正常分支（如乐观锁拦截后提前返回、幂等跳过），一次判空即可表达；用 {@code Optional} 反而容易被当成异常路径 处理。
 */
public final class DomainEvents {

  private DomainEvents() {}

  /**
   * 取聚合已注册事件中首个指定类型的实例。
   *
   * @param events 聚合的已注册事件列表（{@code aggregate.getDomainEvents()}）
   * @param type 目标事件类型
   * @return 首个匹配的事件；未注册该类型时返回 {@code null}
   */
  public static <T extends DomainEvent> T extract(List<DomainEvent> events, Class<T> type) {
    return events.stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
  }
}
