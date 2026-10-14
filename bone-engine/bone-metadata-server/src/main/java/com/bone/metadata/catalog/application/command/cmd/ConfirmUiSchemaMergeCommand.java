package com.bone.metadata.catalog.application.command.cmd;

import lombok.Data;

/**
 * 模板跟随合入确认命令（迁移 0046：租户确认式跟随）。
 *
 * <p>锁版本走 HTTP {@code If-Match} 头（与 ui-schema PUT 同语义），不进 body。
 */
@Data
public class ConfirmUiSchemaMergeCommand {

  /** 冲突段是否取模板新版（true）还是保留实例值（false，仍推进 base_version 消横幅）。 */
  private boolean acceptConflicts;

  /** 预览时的模板版本（合入期间模板又发版 → 409，旧 preview 作废重跑）。 */
  private String templateVersion;
}
