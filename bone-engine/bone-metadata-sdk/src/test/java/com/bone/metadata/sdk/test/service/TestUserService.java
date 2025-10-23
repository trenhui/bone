package com.bone.metadata.sdk.test.service;

import com.bone.metadata.sdk.support.dataSource.annotation.DS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 测试用用户服务类
 * 使用真实的@DS注解标记方法，用于测试注解拦截器功能
 */
@Service
public class TestUserService {
    
    @Autowired
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;
    
    @Autowired
    @Qualifier("slaveJdbcTemplate")
    private JdbcTemplate slaveJdbcTemplate;
    
    /**
     * 使用主数据源创建用户（写操作）
     */
    @DS("master")
    public void createUser(Long id, String name) {
        masterJdbcTemplate.update("INSERT INTO user (id, name) VALUES (?, ?)", id, name);
    }
    
    /**
     * 使用从数据源查询用户（读操作）
     */
    @DS("slave")
    public String getUserNameById(Long id) {
        return slaveJdbcTemplate.queryForObject("SELECT name FROM user WHERE id = ?", String.class, id);
    }
    
    /**
     * 使用主数据源更新用户（写操作）
     */
    @DS("master")
    public void updateUserName(Long id, String newName) {
        masterJdbcTemplate.update("UPDATE user SET name = ? WHERE id = ?", newName, id);
    }
    
    /**
     * 使用自定义数据源（租户数据源）
     */
    @DS("tenantA")
    public String getTenantInfo() {
        // 这个方法在实际测试中会被正确路由到tenantA数据源
        return "tenantA_data";
    }
    
    /**
     * 嵌套数据源调用示例
     * 先从主库获取数据，再从从库验证
     */
    @DS("master")
    public boolean verifyDataSync(Long id) {
        // 从主库获取数据
        String masterName = masterJdbcTemplate.queryForObject("SELECT name FROM user WHERE id = ?", String.class, id);
        
        // 这里模拟在主库事务中查询从库，实际应用中应该避免这种模式
        // 正确的做法是在另一个方法中使用@DS("slave")并单独调用
        return masterName != null && !masterName.isEmpty();
    }
}