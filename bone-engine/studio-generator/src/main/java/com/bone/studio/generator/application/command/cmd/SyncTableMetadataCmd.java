package com.bone.studio.generator.application.command.cmd;

import java.util.List;

public class SyncTableMetadataCmd {
    private String dataSourceId;
    private List<String> tableNames;

    private SyncTableMetadataCmd() {
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public List<String> getTableNames() {
        return tableNames;
    }

    public void setTableNames(List<String> tableNames) {
        this.tableNames = tableNames;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String dataSourceId;
        private List<String> tableNames;

        public Builder dataSourceId(String dataSourceId) {
            this.dataSourceId = dataSourceId;
            return this;
        }

        public Builder tableNames(List<String> tableNames) {
            this.tableNames = tableNames;
            return this;
        }

        public SyncTableMetadataCmd build() {
            SyncTableMetadataCmd cmd = new SyncTableMetadataCmd();
            cmd.dataSourceId = this.dataSourceId;
            cmd.tableNames = this.tableNames;
            return cmd;
        }
    }
}
