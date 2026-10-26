package com.bone.claim.domain.port.out;

/**
 * 发件箱派发出站端口（ADR-008「资金事件零丢失 100%」）。
 *
 * <p>领域层只声明「把一条发件箱记录投递出去」这一能力，不关心投递介质：一期 {@code infrastructure.event.SpringOutboxDispatcher}
 * 以进程内事件总线落地，二期接 MQ 后替换为发往消息中间件，调用方（{@code OutboxApplicationService}）无需改动。
 *
 * <p><b>契约</b>：实现必须「失败即抛」——由调用方统一做重试计数与退避，端口内不得吞异常，否则发件箱会静默停在 PENDING 而无人知晓。
 */
public interface OutboxPort {

  /**
   * 派发一条发件箱记录。
   *
   * @param record 待派发记录（非空）
   * @throws RuntimeException 派发失败时抛出，交由调用方累加 {@code retry_count} / 置 {@code FAILED}
   */
  void dispatch(OutboxRecord record);
}
