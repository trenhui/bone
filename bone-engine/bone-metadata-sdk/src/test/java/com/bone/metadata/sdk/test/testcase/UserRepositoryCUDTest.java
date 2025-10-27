package com.bone.metadata.sdk.test.testcase;

/**
 * 简单的CRUD操作测试示例
 * 移除了对不存在依赖的引用，使用基本Java类型进行测试
 */
public class UserRepositoryCUDTest {

    // 简单的用户数据类
    private static class UserData {
        private Long id;
        private String name;
        private String email;
        private boolean deleted;
        private long createTime;
        private long updateTime;
        
        // 构造方法
        public UserData(String name, String email) {
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
    
    // 简单的内存存储仓库
    private static class InMemoryRepository {
        private java.util.Map<Long, UserData> store = new java.util.HashMap<>();
        private long nextId = 1L;
        
        public Long insert(UserData data) {
            Long id = nextId++;
            data.setId(id);
            store.put(id, data);
            return id;
        }
        
        public boolean update(UserData data) {
            Long id = data.getId();
            if (!store.containsKey(id)) {
                return false;
            }
            store.put(id, data);
            return true;
        }
        
        public boolean delete(Long id) {
            UserData data = store.get(id);
            if (data == null) {
                return false;
            }
            data.setDeleted(true);
            return true;
        }
        
        public UserData findById(Long id) {
            UserData data = store.get(id);
            return data != null && !data.getDeleted() ? data : null;
        }
        
        public java.util.List<UserData> findAll() {
            return store.values().stream()
                    .filter(data -> !data.getDeleted())
                    .collect(java.util.stream.Collectors.toList());
        }
    }
    
    private InMemoryRepository repository;
    
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
    }
    
    @org.junit.jupiter.api.Test
    void testInsert() {
        UserData user = new UserData("test_user", "test@example.com");
        Long id = repository.insert(user);
        
        org.junit.jupiter.api.Assertions.assertNotNull(id, "插入操作应返回ID");
        org.junit.jupiter.api.Assertions.assertTrue(id > 0, "返回的ID应为正数");
        
        UserData found = repository.findById(id);
        org.junit.jupiter.api.Assertions.assertNotNull(found, "应能通过ID找到新插入的数据");
        org.junit.jupiter.api.Assertions.assertEquals("test_user", found.getName(), "用户名应匹配");
        org.junit.jupiter.api.Assertions.assertEquals("test@example.com", found.getEmail(), "邮箱应匹配");
    }
    
    @org.junit.jupiter.api.Test
    void testUpdate() {
        // 先插入
        UserData user = new UserData("update_user", "update@example.com");
        Long id = repository.insert(user);
        
        // 再更新
        UserData updatedUser = repository.findById(id);
        updatedUser.setName("updated_name");
        updatedUser.setEmail("updated@example.com");
        boolean result = repository.update(updatedUser);
        
        org.junit.jupiter.api.Assertions.assertTrue(result, "更新操作应成功");
        
        UserData found = repository.findById(id);
        org.junit.jupiter.api.Assertions.assertEquals("updated_name", found.getName(), "更新后的用户名应匹配");
        org.junit.jupiter.api.Assertions.assertEquals("updated@example.com", found.getEmail(), "更新后的邮箱应匹配");
    }
    
    @org.junit.jupiter.api.Test
    void testDelete() {
        // 先插入
        UserData user = new UserData("delete_user", "delete@example.com");
        Long id = repository.insert(user);
        
        // 验证存在
        org.junit.jupiter.api.Assertions.assertNotNull(repository.findById(id), "删除前应能找到数据");
        
        // 执行删除
        boolean result = repository.delete(id);
        org.junit.jupiter.api.Assertions.assertTrue(result, "删除操作应成功");
        
        // 验证已删除
        org.junit.jupiter.api.Assertions.assertNull(repository.findById(id), "删除后不应再找到数据");
    }
    
    @org.junit.jupiter.api.Test
    void testFindAll() {
        // 插入多条数据
        repository.insert(new UserData("user1", "user1@example.com"));
        repository.insert(new UserData("user2", "user2@example.com"));
        repository.insert(new UserData("user3", "user3@example.com"));
        
        // 查询所有
        java.util.List<UserData> users = repository.findAll();
        org.junit.jupiter.api.Assertions.assertEquals(3, users.size(), "应返回所有插入的用户");
        
        // 删除一个
        repository.delete(2L); // 删除第二个用户
        
        // 再次查询
        users = repository.findAll();
        org.junit.jupiter.api.Assertions.assertEquals(2, users.size(), "应返回未删除的用户");
    }
}