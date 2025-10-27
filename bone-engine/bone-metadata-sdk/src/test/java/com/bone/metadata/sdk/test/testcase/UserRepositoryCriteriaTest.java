package com.bone.metadata.sdk.test.testcase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户仓储条件查询测试
 * 测试基于不同条件的用户数据查询功能
 */
public class UserRepositoryCriteriaTest {
    
    private UserSearchRepository userSearchRepository;
    
    @BeforeEach
    void setUp() {
        userSearchRepository = new UserSearchRepository();
        
        // 预加载测试数据
        userSearchRepository.add(new UserInfo(1L, "Alice", "alice@example.com"));
        userSearchRepository.add(new UserInfo(2L, "Bob", "bob@example.com"));
        userSearchRepository.add(new UserInfo(3L, "Charlie", "charlie@example.com"));
    }
    
    // 用户信息数据模型
    private static class UserInfo {
        private final Long id;
        private final String name;
        private final String email;
        
        public UserInfo(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
        
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        
        @Override
        public String toString() {
            return "UserInfo{id=" + id + ", name='" + name + "', email='" + email + "'}";
        }
    }
    
    // 用户搜索仓储实现
    private static class UserSearchRepository {
        private final List<UserInfo> userInfoList = new ArrayList<>();
        
        public void add(UserInfo userInfo) {
            userInfoList.add(userInfo);
        }
        
        public List<UserInfo> findAllUsers() {
            return new ArrayList<>(userInfoList);
        }
        
        public UserInfo findUserById(Long id) {
            return userInfoList.stream()
                .filter(userInfo -> userInfo.getId().equals(id))
                .findFirst()
                .orElse(null);
        }
        
        public List<UserInfo> findUsersByNameContaining(String nameKeyword) {
            return userInfoList.stream()
                .filter(userInfo -> userInfo.getName().contains(nameKeyword))
                .collect(Collectors.toList());
        }
    }
    
    @Test
    void testFindAllUsers_ShouldReturnAllRecords() {
        List<UserInfo> results = userSearchRepository.findAllUsers();
        Assertions.assertEquals(3, results.size(), "应返回全部3条预加载的用户数据");
    }
    
    @Test
    void testFindUserById_ShouldReturnMatchingUser() {
        // 测试存在的用户
        UserInfo foundUser = userSearchRepository.findUserById(2L);
        Assertions.assertNotNull(foundUser, "应找到ID为2的用户数据");
        Assertions.assertEquals("Bob", foundUser.getName(), "找到的用户名称应匹配");
        
        // 测试不存在的用户
        UserInfo nonExistentUser = userSearchRepository.findUserById(999L);
        Assertions.assertNull(nonExistentUser, "查找不存在的用户ID时应返回null");
    }
    
    @Test
    void testFindUsersByNameContaining_ShouldReturnMatchingUsers() {
        // 测试有匹配结果的查询
        List<UserInfo> matchingUsers = userSearchRepository.findUsersByNameContaining("B");
        Assertions.assertEquals(1, matchingUsers.size(), "应找到1条包含'B'的用户记录");
        Assertions.assertEquals("Bob", matchingUsers.get(0).getName(), "找到的用户名应匹配");
        
        // 测试无匹配结果的查询
        List<UserInfo> noResults = userSearchRepository.findUsersByNameContaining("Z");
        Assertions.assertTrue(noResults.isEmpty(), "找不到匹配项时应返回空列表");
    }
}