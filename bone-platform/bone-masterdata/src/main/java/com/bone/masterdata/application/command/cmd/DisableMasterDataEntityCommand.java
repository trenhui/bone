package com.bone.masterdata.application.command.cmd;

import lombok.Data;

@Data
public class DisableMasterDataEntityCommand {
  private Long id;

  /** true=停用，false=启用（恢复为草稿）。前端 disable 调用默认停用。 */
  private boolean disabled = true;
}
