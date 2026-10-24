package com.bone.workflow.starter.sdk;

import com.bone.core.domain.entity.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code wf_binding}（业务实体 → 流程定义绑定）的 SDK 映射行类。
 *
 * <p>可变行类（{@code SmartRowMapper} 反射回填）；字段与 {@link WfBindingSdkRepository#findBindingRow} 的 SELECT
 * 列清单（{@code entity_code/def_key/start_action}）逐列对应，{@code id} 继承自 {@link Entity}。
 *
 * <p>不映射 {@code enabled/deleted}：二者是查询谓词（{@code enabled = 1 AND deleted = 0}）而非业务数据——
 * 「配了未生效绑定」与「删掉的绑定」都不该进入结果集，在 SQL 层过滤而非应用层判断。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfBindingRow extends Entity<String> {

  private String entityCode;
  private String defKey;
  private String startAction;
}
