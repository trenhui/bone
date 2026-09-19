package com.bone.architecture.fixture.adapter.messaging;

import com.bone.architecture.fixture.domain.service.TaxDomainService;

/**
 * 规则单测夹具：消息入站适配器直调领域服务（`adaptersMustNotDependOnDomainService` 违规）。
 *
 * <p>与 {@code MessagingListenerUsingRepository} 同为 2026-09-19 谓词放宽的负向探针——放宽前 {@code
 * adapter.messaging} 不在 {@code ..adapter..controller..} 覆盖范围内，本形态不会被拦下。
 */
public class MessagingListenerUsingDomainService {

  private final TaxDomainService taxDomainService;

  public MessagingListenerUsingDomainService(TaxDomainService taxDomainService) {
    this.taxDomainService = taxDomainService;
  }

  public String describe() {
    return taxDomainService.getClass().getSimpleName();
  }
}
