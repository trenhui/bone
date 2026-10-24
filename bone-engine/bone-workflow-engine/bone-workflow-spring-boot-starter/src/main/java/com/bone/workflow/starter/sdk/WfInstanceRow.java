package com.bone.workflow.starter.sdk;

import com.bone.core.domain.entity.Entity;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code wf_instance}（流程实例）的 SDK 映射行类。
 *
 * <p><b>为什么必须是可变类而不是 record</b>：SDK 的 {@code SmartRowMapper} 用反射 {@code field.set} 回填列值， record 的
 * final 字段无法赋值——行类只承载「查询结果 → 适配器」的原始数据，领域转换由适配器负责。
 *
 * <p>字段与 {@link WfInstanceSdkRepository} 各 SELECT 的列清单逐列对应；{@code id} 继承自 {@link Entity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfInstanceRow extends Entity<String> {

  private String entityCode;
  private String recordId;
  private String defKey;
  private int defVersion;
  private String currentNodeCode;
  private String phase;
  private String startedBy;
  private Instant startedAt;
  private Instant updatedAt;
}
