package com.bone.metadata.runtime.application;

import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.List;
import java.util.Optional;

/** 动作定义读取（按实体 / 按动作码 / 按当前状态）。底层由 JDBC 适配器实现。 */
public interface RuntimeActionDefRepository {

  Optional<RuntimeActionDef> findByEntityAndCode(
      long tenantId, String entityCode, String actionCode);

  List<RuntimeActionDef> listByEntity(long tenantId, String entityCode);

  List<RuntimeActionDef> listByEntityAndFromStatus(
      long tenantId, String entityCode, String fromStatus);
}
