package com.bone.studio.generator.application.query.qry;

public class DataSourceTablesQry {
    private String dataSourceId;

    private DataSourceTablesQry() {
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

        public DataSourceTablesQry build() {
            DataSourceTablesQry qry = new DataSourceTablesQry();
            qry.dataSourceId = this.dataSourceId;
            return qry;
        }
    }
}
