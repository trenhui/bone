package com.bone.studio.generator.application.query.qry;

public class LoadTablesQry {
    private String dataSourceId;

    private LoadTablesQry() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public static class Builder {
        private String dataSourceId;

        public Builder dataSourceId(String dataSourceId) {
            this.dataSourceId = dataSourceId;
            return this;
        }

        public LoadTablesQry build() {
            LoadTablesQry qry = new LoadTablesQry();
            qry.dataSourceId = dataSourceId;
            return qry;
        }
    }
}