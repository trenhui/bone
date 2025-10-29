package com.bone.metadata.sdk.test.utils;

import com.bone.core.id.IdGenerator;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.sql.Timestamp;
import java.time.Instant;


public class TestDataHelper {

    public static void cleanTestData(NamedParameterJdbcOperations jdbc) {
        jdbc.update("DELETE FROM users", new MapSqlParameterSource());
    }

    public static void setUpPermissionTestData(NamedParameterJdbcOperations jdbc) {
        // 删除原有权限数据
        jdbc.update("DELETE FROM sys_permission", new MapSqlParameterSource());

        // 插入测试数据
        String sql = "INSERT INTO sys_permission (id,biz_identity_code, perm_name, perm_code, perm_type, parent_id, path, component, icon, sort_order, create_time, update_time) " +
                "VALUES (:id, :biz_identity_code,:perm_name, :perm_code, :perm_type, :parent_id, :path, :component, :icon, :sort_order, :create_time, :update_time)";

        // 创建并插入权限数据
        MapSqlParameterSource[] params = new MapSqlParameterSource[] {
                createPermissionParams(1L, "pukang","READ", "READ", 1, 0L, "/read", "ReadComponent", "read-icon", 1),
                createPermissionParams(2L, "pukang","WRITE", "WRITE", 1, 0L, "/write", "WriteComponent", "write-icon", 2),
                createPermissionParams(3L, "pukang","EXECUTE", "EXECUTE", 1, 0L, "/execute", "ExecuteComponent", "execute-icon", 3),
                createPermissionParams(4L, "pukang","DELETE", "DELETE", 1, 0L, "/delete", "DeleteComponent", "delete-icon", 4),
                createPermissionParams(5L, "pukang","MANAGE", "MANAGE", 1, 0L, "/manage", "ManageComponent", "manage-icon", 5)
        };

        // 批量插入权限数据
        jdbc.batchUpdate(sql, params);
    }

    private static MapSqlParameterSource createPermissionParams(Long id,String bizIdentityCode, String permName, String permCode, Integer permType, Long parentId, String path, String component, String icon, Integer sortOrder) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("biz_identity_code",bizIdentityCode)
                .addValue("perm_name", permName)
                .addValue("perm_code", permCode)
                .addValue("perm_type", permType)
                .addValue("parent_id", parentId)
                .addValue("path", path)
                .addValue("component", component)
                .addValue("icon", icon)
                .addValue("sort_order", sortOrder)
                .addValue("create_time", Timestamp.from(Instant.now()))
                .addValue("update_time", Timestamp.from(Instant.now()));
    }


    public static void setUpRoleTestData(NamedParameterJdbcOperations jdbc) {
        jdbc.update("DELETE FROM roles", new MapSqlParameterSource());
        String sql = "INSERT INTO roles (id, role_name, description) VALUES " +
                "(1, 'ADMIN', 'Administrator role'), " +
                "(2, 'USER', 'Standard user role'), " +
                "(3, 'GUEST', 'Guest role with limited access'), " +
                "(4, 'FINANCE_MGR', 'Financial operations and reporting access'), " +
                "(5, 'HR_ADMIN', 'Employee data management permissions'), " +
                "(6, 'READ_ONLY', 'Global read access without write capabilities')";
        jdbc.getJdbcOperations().execute(sql);
    }

    public static void setUpTestData(NamedParameterJdbcOperations jdbc) {
       // cleanTestData(jdbc);
        String sql = "INSERT INTO users (id, name, role_id, create_time, create_by, update_time, update_by, deleted) " +
                "VALUES (:id, :name, :role_id, :create_time, :create_by, :update_time, :update_by, :deleted)";

        // Insert 10 users with varied roles and states
        MapSqlParameterSource[] params = new MapSqlParameterSource[] {
                createUserParams(IdGenerator.generateLongID(), "admin1", 1L, 1001L, false),
                createUserParams(IdGenerator.generateLongID(), "admin2", 1L, 1001L, false),
                createUserParams(IdGenerator.generateLongID(), "user1", 2L, 1002L, false),
                createUserParams(IdGenerator.generateLongID(), "user2", 2L, 1003L, true),
                createUserParams(IdGenerator.generateLongID(), "user3", 3L, 1004L, false),
                createUserParams(IdGenerator.generateLongID(), "admin5", 1L, 1005L, false),
                createUserParams(IdGenerator.generateLongID(), "user7", 2L, 1006L, false),
                createUserParams(IdGenerator.generateLongID(), "admin4", 1L, 1007L, false),
                createUserParams(IdGenerator.generateLongID(), "admin3", 1L, 1008L, false),
                createUserParams(IdGenerator.generateLongID(), "user10", 3L, 1009L, false)
        };
        jdbc.batchUpdate(sql, params);
    }

    private static MapSqlParameterSource createUserParams(Long id, String name, Long roleId, Long createdBy, boolean deleted) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("role_id", roleId)
                .addValue("create_time", Timestamp.from(Instant.now()))
                .addValue("create_by", createdBy)
                .addValue("update_time", Timestamp.from(Instant.now()))
                .addValue("update_by", createdBy)
                .addValue("deleted", deleted ? 1 : 0);
    }
}
