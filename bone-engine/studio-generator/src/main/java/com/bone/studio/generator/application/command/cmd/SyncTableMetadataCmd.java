package com.bone.studio.generator.application.command.cmd;

public class SyncTableMetadataCmd {
    private String dataSourceId;

    private SyncTableMetadataCmd() {
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String dataSourceId;

        public Builder dataSourceId(String dataSourceId) {
            this.dataSourceId = dataSourceId;
            return this;
        }

        public SyncTableMetadataCmd build() {
            SyncTableMetadataCmd cmd = new SyncTableMetadataCmd();
            cmd.dataSourceId = this.dataSourceId;
            return cmd;
        }
    }
}
