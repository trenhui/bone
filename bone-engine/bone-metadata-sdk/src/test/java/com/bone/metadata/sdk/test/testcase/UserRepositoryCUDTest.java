package com.bone.metadata.sdk.test.testcase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户仓储CRUD操作测试
 * 测试用户实体的创建、读取、更新和删除功能
 */
public class UserRepositoryCUDTest {

    // 用户数据模型类
    private static class UserEntity {
        private Long id;
        private String name;
        private String email;
        private boolean deleted;
        private long createTime;
        private long updateTime;
        
        // 构造方法
        public UserEntity(String name, String email) {
            this.name = name;
            this.email = email;
            this.deleted = false;
            this.createTime = System.currentTimeMillis();
            this.updateTime = System.currentTimeMillis();
        }
        
        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; this.updateTime = System.currentTimeMillis(); }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; this.updateTime = System.currentTimeMillis(); }
        public boolean getDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; this.updateTime = System.currentTimeMillis(); }
        public long getCreateTime() { return createTime; }
        public long getUpdateTime() { return updateTime; }
        
        @Override
        public String toString() {
            return "UserData{id=" + id + ", name='" + name + "', email='" + email + "'}";
        }
    }
    
    // 内存存储用户仓储实现
    private static class InMemoryUserRepository {
        private Map<Long, UserEntity> entityStore = new HashMap<>();
        private long nextId = 1L;
        
        public Long insert(UserEntity entity) {
            Long id = nextId++;
            entity.setId(id);
            entityStore.put(id, entity);
            return id;
        }
        
        public boolean update(UserEntity entity) {
            Long id = entity.getId();
            if (!entityStore.containsKey(id)) {
                return false;
            }
            entityStore.put(id, entity);
            return true;
        }
        
        public boolean delete(Long id) {
            UserEntity entity = entityStore.get(id);
            if (entity == null) {
                return false;
            }
            entity.setDeleted(true);
            return true;
        }
        
        public UserEntity findById(Long id) {
            UserEntity entity = entityStore.get(id);
            return entity != null && !entity.getDeleted() ? entity : null;
        }
        
        public List<UserEntity> findAll() {
            return entityStore.values().stream()
                    .filter(entity -> !entity.getDeleted())
                    .collect(Collectors.toList());
        }
    }
    
    private InMemoryUserRepository userRepository;
    
    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
    }
    
    @Test
    void testInsertUser_ShouldReturnGeneratedId() {
        // 创建测试用户实体
        UserEntity userEntity = new UserEntity("test_user", "test@example.com");
        Long generatedId = userRepository.insert(userEntity);
        
        // 验证ID生成正确
        Assertions.assertNotNull(generatedId, "插入操作应返回有效的ID");
        Assertions.assertTrue(generatedId > 0, "返回的ID应为正数");
        
        // 验证数据能被正确检索
        UserEntity foundEntity = userRepository.findById(generatedId);
        Assertions.assertNotNull(foundEntity, "应能通过ID找到新插入的数据");
        Assertions.assertEquals("test_user", foundEntity.getName(), "用户名应正确保存");
        Assertions.assertEquals("test@example.com", foundEntity.getEmail(), "邮箱应正确保存");
    }
    
    @Test
    void testUpdateUser_ShouldModifyExistingRecord() {
        // 准备测试数据
        UserEntity userEntity = new UserEntity("update_user", "update@example.com");
        Long id = userRepository.insert(userEntity);
        
        // 执行更新操作
        UserEntity updatedEntity = userRepository.findById(id);
        updatedEntity.setName("updated_name");
        updatedEntity.setEmail("updated@example.com");
        boolean updateResult = userRepository.update(updatedEntity);
        
        // 验证更新成功
        Assertions.assertTrue(updateResult, "更新操作应成功");
        
        // 验证数据已更新
        UserEntity foundEntity = userRepository.findById(id);
        Assertions.assertEquals("updated_name", foundEntity.getName(), "用户名应被正确更新");
        Assertions.assertEquals("updated@example.com", foundEntity.getEmail(), "邮箱应被正确更新");
    }
    
    @Test
    void testDeleteUser_ShouldMarkAsDeleted() {
        // 准备测试数据
        UserEntity userEntity = new UserEntity("delete_user", "delete@example.com");
        Long id = userRepository.insert(userEntity);
        
        // 验证数据存在
        Assertions.assertNotNull(userRepository.findById(id), "删除前应能找到数据");
        
        // 执行删除操作
        boolean deleteResult = userRepository.delete(id);
        Assertions.assertTrue(deleteResult, "删除操作应成功");
        
        // 验证数据已被删除（软删除）
        Assertions.assertNull(userRepository.findById(id), "删除后不应再找到数据");
    }
    
    @Test
    void testFindAllUsers_ShouldReturnOnlyActiveUsers() {
        // 准备测试数据
        userRepository.insert(new UserEntity("user1", "user1@example.com"));
        userRepository.insert(new UserEntity("user2", "user2@example.com"));
        userRepository.insert(new UserEntity("user3", "user3@example.com"));
        
        // 验证初始数据数量
        List<UserEntity> activeUsers = userRepository.findAll();
        Assertions.assertEquals(3, activeUsers.size(), "应返回所有活跃用户");
        
        // 删除一个用户
        userRepository.delete(2L); // 删除第二个用户
        
        // 验证剩余活跃用户数量
        activeUsers = userRepository.findAll();
        Assertions.assertEquals(2, activeUsers.size(), "应只返回未被删除的用户");
    }
}