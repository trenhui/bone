package com.bone.metadata.catalog.domain.service;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.model.iam.IamApplicationRef;
import com.bone.metadata.catalog.domain.repository.IamApplicationRepository;
import lombok.RequiredArgsConstructor;

/**
 * 应用存在性校验器（下游/Supplier 关系中的 Customer 侧守卫）。
 *
 * <p>metadata 上下文不再拥有应用聚合根，模块(Module)必须挂在 IAM 上下文的 {@code bone_application} 之下。本校验器在 metadata
 * 写入模块前确认所引用的 {@code appId} 在 IAM 的 {@code bone_application} 中真实存在，维持跨上下文引用一致性。
 */
@RequiredArgsConstructor
public class IamApplicationValidator {

  private final IamApplicationRepository iamApplicationRepository;

  /** 若指定 appId 在 IAM 应用中不存在，抛出异常。 */
  public void requireExists(Long appId) {
    if (appId == null) {
      throw BizException.of("应用ID不能为空");
    }
    IamApplicationRef ref = iamApplicationRepository.findById(appId);
    if (ref == null) {
      throw BizException.of("所属应用不存在: " + appId);
    }
  }
}
