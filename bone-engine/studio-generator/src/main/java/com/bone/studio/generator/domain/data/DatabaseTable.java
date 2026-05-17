package com.bone.studio.generator.domain.data;

import java.util.List;

public class DatabaseTable {
    private String tableName;
    private String tableComment;
    private List<TableColumn> columns;
    private String primaryKey;
    private List<String> indexes;
    /** 0-GENERATIVE 1-RUNTIME */
    private int deliveryMode;

    private DatabaseTable() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTableName() {
        return tableName;
    }

    public String getTableComment() {
        return tableComment;
    }

    public List<TableColumn> getColumns() {
        return columns;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public List<String> getIndexes() {
        return indexes;
    }

    public int getDeliveryMode() {
        return deliveryMode;
    }

    public boolean isRuntimeDelivery() {
        return deliveryMode == 1;
    }

    public static class Builder {
        private String tableName;
        private String tableComment;
        private List<TableColumn> columns;
        private String primaryKey;
        private List<String> indexes;
        private int deliveryMode;

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder tableComment(String tableComment) {
            this.tableComment = tableComment;
            return this;
        }

        public Builder columns(List<TableColumn> columns) {
            this.columns = columns;
            return this;
        }

        public Builder primaryKey(String primaryKey) {
            this.primaryKey = primaryKey;
            return this;
        }

        public Builder indexes(List<String> indexes) {
            this.indexes = indexes;
            return this;
        }

        public Builder deliveryMode(int deliveryMode) {
            this.deliveryMode = deliveryMode;
            return this;
        }

        public DatabaseTable build() {
            DatabaseTable table = new DatabaseTable();
            table.tableName = tableName;
            table.tableComment = tableComment;
            table.columns = columns;
            table.primaryKey = primaryKey;
            table.indexes = indexes;
            table.deliveryMode = deliveryMode;
            return table;
        }
    }
}