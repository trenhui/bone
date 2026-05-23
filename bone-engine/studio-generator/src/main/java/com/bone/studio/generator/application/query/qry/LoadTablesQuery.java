package com.bone.studio.generator.application.query.qry;

public class LoadTablesQuery {
    private String dataSourceId;

    private LoadTablesQuery() {
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

        public LoadTablesQuery build() {
            LoadTablesQuery qry = new LoadTablesQuery();
            qry.dataSourceId = dataSourceId;
            return qry;
        }
    }
}