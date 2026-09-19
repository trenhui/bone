package com.bone.architecture.fixture.adapter.schedule;

import com.bone.architecture.fixture.domain.repository.OrderAggregateRepository;

/**
 * 规则单测夹具：定时 Job 直注写侧仓储（ADR-0030 代价 C3 **授权形态**，规则必须放行）。
 *
 * <p><b>为何要有这个夹具</b>：{@code adaptersMustNotDependOnDomainRepository} 对 {@code ..adapter.schedule..}
 * 开了受控例外（全租户运维扫描需直连域仓储）。例外是<strong>有意设计</strong>而非疏漏， 本夹具把这一点固化成断言：规则若被误改成「连 schedule
 * 一起禁」，对应测试必须失败——防止有人把受控旁路当违规修掉。
 *
 * <p>注意：授权的是「依赖域仓储」这一件事，不是无限授权。滥用面由模块级 {@code schedule_only_calls_all_tenants_repository_methods}
 * 类规则收紧（只许调 {@code *AllTenants} 方法）。
 */
public class ScheduleJobUsingRepository {

  private final OrderAggregateRepository repository;

  public ScheduleJobUsingRepository(OrderAggregateRepository repository) {
    this.repository = repository;
  }

  public boolean cleanup(Long id) {
    return repository.remove(id);
  }
}
