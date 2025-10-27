package com.bone.metadata.sdk.support.dataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 简化的用户服务实现
 */
public class UserService {
    
    private DataSource dataSource;
    
    public UserService() {
    }
    
    public UserService(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * 根据ID获取用户名
     */
    public String getUserById(Long userId) {
        String sql = "SELECT name FROM user WHERE id = ?";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            
            ps.setLong(1, userId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
                return null;
            }
        } catch (SQLException e) {
            System.err.println("获取用户信息失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 根据用户名获取用户ID
     */
    public Long getUserIdByName(String username) {
        String sql = "SELECT id FROM user WHERE name = ?";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            
            ps.setString(1, username);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
                return null;
            }
        } catch (SQLException e) {
            System.err.println("获取用户信息失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 创建用户
     */
    public boolean createUser(Long userId, String username) {
        String sql = "INSERT INTO user (id, name) VALUES (?, ?)";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            
            ps.setLong(1, userId);
            ps.setString(2, username);
            
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("创建用户失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 更新用户
     */
    public boolean updateUser(Long userId, String newUsername) {
        String sql = "UPDATE user SET name = ? WHERE id = ?";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            
            ps.setString(1, newUsername);
            ps.setLong(2, userId);
            
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("更新用户失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 删除用户
     */
    public boolean deleteUser(Long userId) {
        String sql = "DELETE FROM user WHERE id = ?";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            
            ps.setLong(1, userId);
            
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("删除用户失败: " + e.getMessage());
            return false;
        }
    }
}