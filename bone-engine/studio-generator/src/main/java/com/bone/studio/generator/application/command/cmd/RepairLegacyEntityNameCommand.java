package com.bone.studio.generator.application.command.cmd;

/**
 * 一次性运维修复命令：将存量未转驼峰的 {@code custom_entity_name} 收敛为 PascalCase。
 *
 * <p>默认 {@code execute=false} 仅预览受影响行（安全）；携带 {@code execute=true} 才真正写入。 幂等、可重复执行。
 */
public class RepairLegacyEntityNameCommand {

  /** 是否真正写入；false 仅预览（默认，安全）。 */
  private boolean execute = false;

  /** 可选：仅修复指定数据源（dataSourceId），不传则全量。 */
  private String dataSourceId;

  public boolean isExecute() {
    return execute;
  }

  public void setExecute(boolean execute) {
    this.execute = execute;
  }

  public String getDataSourceId() {
    return dataSourceId;
  }

  public void setDataSourceId(String dataSourceId) {
    this.dataSourceId = dataSourceId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private boolean execute = false;
    private String dataSourceId;

    public Builder execute(boolean execute) {
      this.execute = execute;
      return this;
    }

    public Builder dataSourceId(String dataSourceId) {
      this.dataSourceId = dataSourceId;
      return this;
    }

    public RepairLegacyEntityNameCommand build() {
      RepairLegacyEntityNameCommand command = new RepairLegacyEntityNameCommand();
      command.execute = this.execute;
      command.dataSourceId = this.dataSourceId;
      return command;
    }
  }
}
