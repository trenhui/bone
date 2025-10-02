-- resources/sql/com/example/UserRepository/queryWithFragment.sql
SELECT <include refid="userFields"/> FROM ${tableName} u WHERE status = :status