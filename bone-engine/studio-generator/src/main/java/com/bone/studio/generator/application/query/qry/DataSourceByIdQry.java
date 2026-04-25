package com.bone.studio.generator.application.query.qry;

public class DataSourceByIdQry {
    private String id;

    private DataSourceByIdQry() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public static class Builder {
        private String id;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public DataSourceByIdQry build() {
            DataSourceByIdQry qry = new DataSourceByIdQry();
            qry.id = id;
            return qry;
        }
    }
}