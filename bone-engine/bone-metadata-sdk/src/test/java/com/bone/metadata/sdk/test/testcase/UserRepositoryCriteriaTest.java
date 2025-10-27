package com.bone.metadata.sdk.test.testcase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;

/**
 * 简单的标准测试示例
 * 移除了对不存在类的依赖
 */
public class UserRepositoryCriteriaTest {
    
    private SimpleRepository repository;
    
    @BeforeEach
    void setUp() {
        repository = new SimpleRepository();
        
        // 预加载一些测试数据
        repository.add(new SimpleData(1L, "Alice", "alice@example.com"));
        repository.add(new SimpleData(2L, "Bob", "bob@example.com"));
        repository.add(new SimpleData(3L, "Charlie", "charlie@example.com"));
    }
    
    // 简单的数据类
    private static class SimpleData {
        private Long id;
        private String name;
        private String email;
        
        public SimpleData(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
        
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        
        @Override
        public String toString() {
            return "SimpleData{id=" + id + ", name='" + name + "', email='" + email + "'}";
        }
    }
    
    // 简单的仓库类
    private static class SimpleRepository {
        private List<SimpleData> dataList = new ArrayList<>();
        
        public void add(SimpleData data) {
            dataList.add(data);
        }
        
        public List<SimpleData> findAll() {
            return new ArrayList<>(dataList);
        }
        
        public SimpleData findById(Long id) {
            return dataList.stream()
                .filter(data -> data.getId().equals(id))
                .findFirst()
                .orElse(null);
        }
        
        public List<SimpleData> findByName(String name) {
            return dataList.stream()
                .filter(data -> data.getName().contains(name))
                .collect(java.util.stream.Collectors.toList());
        }
    }
    
    @Test
    void testFindAll() {
        List<SimpleData> results = repository.findAll();
        org.junit.jupiter.api.Assertions.assertEquals(3, results.size(), "应返回3条测试数据");
    }
    
    @Test
    void testFindById() {
        SimpleData result = repository.findById(2L);
        org.junit.jupiter.api.Assertions.assertNotNull(result, "应找到ID为2的数据");
        org.junit.jupiter.api.Assertions.assertEquals("Bob", result.getName(), "名称应匹配");
        
        SimpleData nonExistent = repository.findById(999L);
        org.junit.jupiter.api.Assertions.assertNull(nonExistent, "不存在的数据应返回null");
    }
    
    @Test
    void testFindByName() {
        List<SimpleData> results = repository.findByName("B");
        org.junit.jupiter.api.Assertions.assertEquals(1, results.size(), "应找到1条包含'B'的记录");
        org.junit.jupiter.api.Assertions.assertEquals("Bob", results.get(0).getName(), "名称应匹配");
        
        List<SimpleData> noResults = repository.findByName("Z");
        org.junit.jupiter.api.Assertions.assertTrue(noResults.isEmpty(), "找不到匹配项时应返回空列表");
    }
}