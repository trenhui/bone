package com.bone.metadata.catalog.domain.service;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.model.IamModuleRef;
import com.bone.metadata.catalog.domain.repository.IamModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模块存在性校验器（下游/Supplier 关系中的 Customer 侧守卫）。
 *
 * <p>metadata 上下文不再拥有模块聚合根，实体(Entity)/字段(Field)必须挂在 IAM 上下文的 {@code bone_module} 之下。本校验器在 metadata
 * 写入实体/字段前确认所引用的 {@code moduleId} 在 IAM 的 {@code bone_module} 中真实存在，维持跨上下文引用一致性。
 */
@Component
@RequiredArgsConstructor
public class IamModuleValidator {

  private final IamModuleRepository iamModuleRepository;

  /** 若指定 moduleId 在 IAM 模块中不存在，抛出异常。 */
  public void requireExists(Long moduleId) {
    if (moduleId == null) {
      throw BizException.of("模块ID不能为空");
    }
    IamModuleRef ref = iamModuleRepository.findById(moduleId);
    if (ref == null) {
      throw BizException.of("所属模块不存在: " + moduleId);
    }
  }
}
