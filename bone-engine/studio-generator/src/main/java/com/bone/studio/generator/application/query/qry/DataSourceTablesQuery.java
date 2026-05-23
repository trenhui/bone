package com.bone.studio.generator.application.query.qry;

public class DataSourceTablesQuery {
    private String dataSourceId;

    private DataSourceTablesQuery() {
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

        public DataSourceTablesQuery build() {
            DataSourceTablesQuery qry = new DataSourceTablesQuery();
            qry.dataSourceId = this.dataSourceId;
            return qry;
        }
    }
}
