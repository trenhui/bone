package com.bone.metadata.sdk.query.dsl;

import com.bone.metadata.sdk.query.dsl.QueryBuilder.JoinClause;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试连接查询功能，特别是使用方法引用替代字符串形式的关联查询条件
 */
public class JoinQueryTest {

    @Test
    public void testJoinWithMethodReference() {
        // 测试使用方法引用进行连接查询
        try {
            List<User> users = QueryBuilder.from(User.class)
                    .join(Role.class)
                    .on(User::getRoleId, Role::getId)
                    .whereJoin(Role::getCode).eq("admin")
                    .list();
            
            // 验证查询不会抛出异常
            assertNotNull(users, "查询结果不应为null");
        } catch (Exception e) {
            fail("测试失败，抛出了异常: " + e.getMessage());
        }
    }
    
    // 模拟User类，用于测试
    public static class User {
        private String name;
        private Integer roleId;
        private Integer id;
        
        public User() {
        }
        
        public User(String name, Integer roleId, Integer id) {
            this.name = name;
            this.roleId = roleId;
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Integer getRoleId() {
            return roleId;
        }
        
        public void setRoleId(Integer roleId) {
            this.roleId = roleId;
        }
        
        public Integer getId() {
            return id;
        }
        
        public void setId(Integer id) {
            this.id = id;
        }
        
        @Override
        public String toString() {
            return "User(name=" + name + ", roleId=" + roleId + ", id=" + id + ")";
        }
    }
    
    // 模拟Role类，用于测试
    public static class Role {
        private String code;
        private Integer id;
        private String name;
        
        public Role() {
        }
        
        public Role(String code, Integer id, String name) {
            this.code = code;
            this.id = id;
            this.name = name;
        }
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public Integer getId() {
            return id;
        }
        
        public void setId(Integer id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return "Role(code=" + code + ", id=" + id + ", name=" + name + ")";
        }
    }
}