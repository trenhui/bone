package com.bone.workflow.starter.sdk;

import com.bone.core.domain.entity.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code wf_process_def}（流程定义）的 SDK 映射行类。
 *
 * <p>可变行类（{@code SmartRowMapper} 反射回填）；字段与 {@link WfProcessDefSdkRepository} 各 SELECT 的列清单 （{@code
 * def_key/def_name/def_version/definition_json}）逐列对应，{@code id} 继承自 {@link Entity}。
 *
 * <p>{@code definitionJson} 是定义 JSON 的<b>原文</b>——反序列化（含损坏时的 fail-loudly）由适配器 {@code
 * SdkWorkflowDefinitionRepository} 负责，行类不做任何解析。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfProcessDefRow extends Entity<String> {

  private String defKey;
  private String defName;
  private int defVersion;
  private String definitionJson;
}
