package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 逆向建模命令：从存量物理表导入为目录实体（UC-IMP）。
 *
 * <p>真实场景：企业已有业务表要先纳入元数据治理，再由平台增量扩展字段；若只支持「模型优先、发布建表」， 存量表就必须人工重录，既慢又易与物理表漂移。
 *
 * <p>编码 / 名称缺省时由表名推导（{@code t_order → 实体编码 t_order}），表名为必填且必须真实存在（缺表直接拒绝， 避免建出与物理库脱节的空模型）。
 *
 * <p>{@code dryRun=true} 只回采集结果不落库，供前端「导入预览」向导使用。
 */
@Data
public class ImportMetaEntityFromTableCommand {

  @NotBlank(message = "物理表名不能为空")
  private String tableName;

  /** 实体编码；缺省取表名。 */
  private String code;

  /** 实体名称；缺省取编码。 */
  private String name;

  /** 显示名；缺省取表名。 */
  private String displayName;

  private String description;

  /** 交付模式（0=GENERATIVE / 1=RUNTIME）。缺省 1：存量表已物理存在，导入目的就是纳入运行时元数据面统一读写与治理。 */
  private Integer deliveryMode;

  /** 是否把平台保留列（id/tenant_id/version/deleted/审计列）也建模为业务字段。默认 false。 */
  private Boolean includeReserved;

  /** 试运行：只回采集结果，不写库。 */
  private Boolean dryRun;
}
