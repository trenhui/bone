package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 实体复制命令（UC-W2 流程 B：从已有模型复制，跨应用复用场景）。
 *
 * <p>编码与表名为必填（唯一键在本租户内全局占用，不能由后端代生成后静默撞键）； 名称/显示名缺省时由应用服务以「源名_copy / 源显示名 副本」补齐；
 * 目标模块缺省时保持与源实体同模块——目标模块的应用建模角色（G1②）在 {@code createEntity} 内统一强制。
 */
@Data
public class CopyMetaEntityCommand {

  @NotBlank(message = "新实体编码不能为空")
  private String code;

  @NotBlank(message = "新实体表名不能为空")
  private String tableName;

  private String name;

  private String displayName;

  private String description;

  /** 目标模块；缺省 = 与源实体同模块 */
  private Long targetModuleId;
}
