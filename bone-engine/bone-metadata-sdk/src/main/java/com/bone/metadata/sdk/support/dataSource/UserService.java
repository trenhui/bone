package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 示例服务类，演示数据源切换的使用
 */
@Service
public class UserService {
    
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * 查询用户详情 - 默认使用主库
     */
    public String getUserById(Long userId) {
        log.info("Querying user by id: {}", userId);
        
        // 使用默认数据源（主库）
        return jdbcTemplate.queryForObject(
                "SELECT name FROM user WHERE id = ?", 
                new Object[]{userId}, 
                String.class);
    }
    
    /**
     * 查询用户列表 - 使用从库，提高主库性能
     */
    @DataSourceSwitch("slave")
    public int getUserCount() {
        log.info("Counting users from slave database");
        
        // 使用从库数据源
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user", 
                Integer.class);
    }
    
    /**
     * 保存用户信息 - 强制使用主库
     */
    @DataSourceSwitch("master")
    @Transactional(rollbackFor = Exception.class)
    public void createUser(Long userId, String username) {
        log.info("Creating user: {} with id: {}", username, userId);
        
        // 使用主库数据源，确保写操作在主库执行
        jdbcTemplate.update(
                "INSERT INTO user (id, name) VALUES (?, ?)", 
                userId, username);
    }
    
    /**
     * 更新用户信息 - 强制使用主库
     */
    @DataSourceSwitch(value = "master", force = true) // 设置force=true，在事务中也强制使用主库
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, String newUsername) {
        log.info("Updating user: {} to new username: {}", userId, newUsername);
        
        // 使用主库数据源，确保写操作在主库执行
        jdbcTemplate.update(
                "UPDATE user SET name = ? WHERE id = ?", 
                newUsername, userId);
    }
    
    /**
     * 嵌套数据源切换示例
     */
    @DataSourceSwitch("master")
    public void nestedDataSourceExample(Long userId) {
        log.info("Outer method using master datasource");
        
        // 这里使用主库
        jdbcTemplate.update("UPDATE user SET last_login = CURRENT_TIMESTAMP WHERE id = ?", userId);
        
        // 在方法内部切换到从库读取数据
        DataSourceContextHolder.executeInDataSource("slave", () -> {
            log.info("Inner code block using slave datasource");
            // 这里使用从库
            String username = jdbcTemplate.queryForObject(
                    "SELECT name FROM user WHERE id = ?", 
                    new Object[]{userId}, 
                    String.class);
            log.info("Retrieved username: {} from slave", username);
        });
        
        // 回到外部方法，应该继续使用主库
        log.info("Back to outer method using master datasource");
    }
}