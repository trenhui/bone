package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * L0→L1 迁移命令（界面设计器字典打通 v1.1 §5.3）。
 *
 * <p><b>为什么自带「确认」字段</b>：同码不同义 / 同义不同码是<b>告警不阻断</b>的冲突，机器不替人做语义裁决； 前端必须把冲突清单展示给建模者、拿到明确确认后才回传 {@code
 * confirmConflicts=true}。 这样「静默按错误语义绑定」在协议层就不可能发生。
 */
@Data
public class MigrateToDictCommand {

  /** 目标值域编码（平台字典 sys_dict_type.code）。 */
  @NotBlank(message = "迁移目标值域编码不能为空")
  private String typeCode;

  /**
   * 本地多出项策略：{@code MERGE_INTO_DICT}（并入字典）或 {@code KEEP_LOCAL_OVERRIDE}（保留为本地追加项）。
   *
   * <p>缺省按 KEEP_LOCAL_OVERRIDE——不擅自往共享值域里写数据（写别人的真源是更重的动作，必须显式选）。
   */
  private String localOnlyStrategy;

  /** 是否已确认冲突告警（存在冲突时必填 true，否则 409）。 */
  private boolean confirmConflicts;
}
