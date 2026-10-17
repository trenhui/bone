package com.bone.metadata.runtime.application;

import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.List;
import org.springframework.stereotype.Service;

/** 动作元数据查询：供前端按实体 / 按当前状态列出可用动作，渲染操作按钮。 */
@Service
public class RuntimeActionMetaApplicationService {

  private final RuntimeActionDefRepository actionDefRepository;

  public RuntimeActionMetaApplicationService(RuntimeActionDefRepository actionDefRepository) {
    this.actionDefRepository = actionDefRepository;
  }

  /** 某实体的全部动作定义（前端一次拉取后按 from_status 分组）。 */
  public List<RuntimeActionDef> listByEntity(long tenantId, String entityCode) {
    return actionDefRepository.listByEntity(tenantId, entityCode);
  }

  /** 某实体在某状态下可触发的动作（前端按行状态过滤用）。 */
  public List<RuntimeActionDef> listByStatus(long tenantId, String entityCode, String status) {
    return actionDefRepository.listByEntityAndFromStatus(tenantId, entityCode, status);
  }
}
