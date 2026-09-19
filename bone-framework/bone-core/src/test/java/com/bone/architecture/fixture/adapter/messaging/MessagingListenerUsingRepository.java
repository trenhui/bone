package com.bone.architecture.fixture.adapter.messaging;

import com.bone.architecture.fixture.domain.repository.OrderAggregateRepository;

/**
 * 规则单测夹具：消息入站适配器直注写侧仓储（`adaptersMustNotDependOnDomainRepository` 违规）。
 *
 * <p><b>为何要有这个夹具</b>：2026-09-19 三条入站规则的谓词由 {@code ..adapter..controller..} 放宽为 {@code
 * ..adapter..}。放宽前本类形态（{@code adapter.messaging}）会<strong>整条逃逸</strong>且门禁永远绿；
 * 本夹具是这次放宽的<strong>负向探针</strong>——规则若退回旧谓词，对应测试必须失败。
 */
public class MessagingListenerUsingRepository {

  private final OrderAggregateRepository repository;

  public MessagingListenerUsingRepository(OrderAggregateRepository repository) {
    this.repository = repository;
  }

  public boolean cleanup(Long id) {
    return repository.remove(id);
  }
}
