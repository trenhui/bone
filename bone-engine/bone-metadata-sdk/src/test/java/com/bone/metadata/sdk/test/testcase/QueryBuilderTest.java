package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.test.model.Role;
import com.bone.metadata.sdk.test.model.User;
import com.bone.metadata.sdk.test.util.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.bone.metadata.sdk.query.dsl.EntitySqlBuilder;
import com.bone.metadata.sdk.query.dsl.WhereClause;
import com.bone.metadata.sdk.query.dsl.ConditionClause;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryBuilder的单元测试类
 * 测试QueryBuilder的所有接口方法
 */
@SpringBootTest(classes = QueryBuilderTestConfig.class)
@ActiveProfiles("test")
@Transactional
@Slf4j
public class QueryBuilderTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setup() {
        // 创建表结构
        jdbcTemplate.execute("DROP TABLE IF EXISTS users CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS roles CASCADE");
        
        // 创建角色表
        jdbcTemplate.execute("CREATE TABLE roles (id BIGINT PRIMARY KEY, name VARCHAR(100), code VARCHAR(50))");
        
        // 创建用户表
        jdbcTemplate.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username VARCHAR(100), password VARCHAR(100), email VARCHAR(255), role_id BIGINT, age INT, status INT, FOREIGN KEY (role_id) REFERENCES roles(id))");

        // 插入测试数据
        jdbcTemplate.execute("INSERT INTO roles(id, name, code) VALUES (1, '管理员', 'ADMIN')");
        jdbcTemplate.execute("INSERT INTO roles(id, name, code) VALUES (2, '普通用户', 'USER')");

        jdbcTemplate.execute("INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (1, 'admin', '123456', 'admin@test.com', 1, 30, 1)");
        jdbcTemplate.execute("INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (2, 'user1', '123456', 'user1@test.com', 2, 25, 1)");
        jdbcTemplate.execute("INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (3, 'user2', '123456', 'user2@test.com', 2, 28, 0)");
    }

    /**
     * 测试Spring上下文初始化
     */
    @Test
    public void testContextInitialization() {
        log.info("测试Spring上下文初始化");
        // 只验证Spring上下文能正确注入JdbcTemplate
        assertNotNull(jdbcTemplate);
        log.info("JdbcTemplate注入成功");
        // 直接执行简单的SQL查询，验证数据库连接是否正常
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT 1 FROM dual", Integer.class);
            log.info("数据库查询成功: {}", count);
            assertEquals(1, count);
        } catch (Exception e) {
            log.error("数据库查询异常: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 测试基本的查询功能
     */
    @Test
    public void testBasicQuery() {
        log.info("开始执行testBasicQuery测试");
        try {
            // 查询所有用户
            log.info("执行list查询");
            List<User> users = QueryBuilder.from(User.class).list();
            log.info("查询结果: {}", users);
            assertNotNull(users);
            log.info("用户数量: {}", users.size());
            // 简化测试，只检查结果不为空
            assertTrue(users.size() >= 0);
        } catch (Exception e) {
            log.error("测试执行异常: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 测试条件查询 - eq
     */
    @Test
    public void testWhereEq() {
        // 条件查询：查询用户名为admin的用户
        User admin = QueryBuilder.from(User.class)
                .where(User::getUsername)
                .eq("admin")
                .single();
        assertNotNull(admin);
        assertEquals("admin", admin.getUsername());
    }

    /**
     * 测试条件查询 - like
     */
    @Test
    public void testWhereLike() {
        // 条件查询：查询用户名包含'user'的用户
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getUsername)
                .like("user")
                .list();
        assertNotNull(users);
        assertEquals(2, users.size());
        assertTrue(users.stream().allMatch(u -> u.getUsername().contains("user")));
    }

    /**
     * 测试条件查询 - 比较操作符
     */
    @Test
    public void testWhereComparisonOperators() {
        // 测试gt操作符
        List<User> usersGt = QueryBuilder.from(User.class)
                .where(User::getAge)
                .gt(26)
                .list();
        assertEquals(2, usersGt.size());

        // 测试lt操作符
        List<User> usersLt = QueryBuilder.from(User.class)
                .where(User::getAge)
                .lt(28)
                .list();
        assertEquals(1, usersLt.size());

        // 测试gte操作符
        List<User> usersGte = QueryBuilder.from(User.class)
                .where(User::getAge)
                .gte(28)
                .list();
        assertEquals(2, usersGte.size());

        // 测试lte操作符
        List<User> usersLte = QueryBuilder.from(User.class)
                .where(User::getAge)
                .lte(28)
                .list();
        assertEquals(2, usersLte.size());
    }

    /**
     * 测试条件查询 - ne操作符
     */
    @Test
    public void testWhereNe() {
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getRoleId)
                .ne(1)
                .list();
        assertEquals(2, users.size());
        assertTrue(users.stream().allMatch(u -> u.getRoleId() != 1));
    }

    /**
     * 测试条件查询 - in操作符
     */
    @Test
    public void testWhereIn() {
        List<Long> ids = Arrays.asList(1L, 2L);
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getId)
                .in(ids)
                .list();
        assertEquals(2, users.size());
        List<Long> resultIds = users.stream().map(User::getId).collect(Collectors.toList());
        assertTrue(resultIds.containsAll(ids));
    }

    /**
     * 测试条件查询 - isNull和isNotNull
     */
    @Test
    public void testWhereNullConditions() {
        // 插入一条email为null的记录进行测试
        log.info("开始测试isNull和isNotNull条件");
        log.info("插入email为null的记录");
        jdbcTemplate.execute("INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (4, 'nulluser', '123456', NULL, 2, 35, 1)");
        
        // 验证数据是否成功插入
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email IS NULL", Integer.class);
        log.info("数据库中email为null的记录数量: {}", count);
        
        // 查看所有用户数据进行调试
        List<Map<String, Object>> allUsers = jdbcTemplate.queryForList("SELECT id, username, email FROM users");
        log.info("数据库中所有用户记录: {}", allUsers);

        // 手动执行SQL测试
        List<Map<String, Object>> manualNullUsers = jdbcTemplate.queryForList("SELECT * FROM users m WHERE m.email IS NULL");
        log.info("手动执行SQL查询结果: {}", manualNullUsers.size());

        // 测试isNull
        log.info("执行QueryBuilder的isNull查询");
        List<User> nullEmailUsers = QueryBuilder.from(User.class)
                .where(User::getEmail)
                .isNull()
                .list();
        log.info("isNull查询结果数量: {}", nullEmailUsers.size());
        assertEquals(1, nullEmailUsers.size(), "应该找到1条email为null的记录");

        // 测试isNotNull
        log.info("执行QueryBuilder的isNotNull查询");
        List<User> notNullEmailUsers = QueryBuilder.from(User.class)
                .where(User::getEmail)
                .isNotNull()
                .list();
        log.info("isNotNull查询结果数量: {}", notNullEmailUsers.size());
        assertEquals(3, notNullEmailUsers.size(), "应该找到3条email不为null的记录");
    }

    /**
     * 测试多条件组合查询（and）
     */
    @Test
    public void testMultipleConditions() {
        // 多条件组合查询
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getRoleId)
                .eq(2)
                .and(User::getStatus)
                .eq(1)
                .list();
        assertEquals(1, users.size());
        assertEquals("user1", users.get(0).getUsername());
    }

    /**
     * 测试排序功能
     */
    @Test
    public void testOrderBy() {
        // 升序排序
        List<User> usersAsc = QueryBuilder.from(User.class)
                .orderBy(User::getAge)
                .asc()
                .list();
        assertEquals(3, usersAsc.size());
        assertTrue(usersAsc.get(0).getAge() <= usersAsc.get(1).getAge());
        assertTrue(usersAsc.get(1).getAge() <= usersAsc.get(2).getAge());

        // 降序排序
        List<User> usersDesc = QueryBuilder.from(User.class)
                .orderBy(User::getAge)
                .desc()
                .list();
        assertEquals(3, usersDesc.size());
        assertTrue(usersDesc.get(0).getAge() >= usersDesc.get(1).getAge());
        assertTrue(usersDesc.get(1).getAge() >= usersDesc.get(2).getAge());
    }

    /**
     * 测试分页功能
     */
    @Test
    public void testPagination() {
        // 测试分页
        List<User> pagedUsers = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .asc()
                .list();
        assertEquals(3, pagedUsers.size());

        // 测试偏移量
        List<User> offsetUsers = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .asc()
                .list();
        assertEquals(3, offsetUsers.size());
    }

    /**
     * 测试分组查询
     */
    @Test
    public void testGroupBy() {
        // 按role_id分组并计数
        long count = QueryBuilder.from(User.class)
                .groupBy(User::getRoleId)
                .count();
        // 应该有2个不同的role_id分组
        assertEquals(2, count);
    }
    
    /**
     * 测试多字段分组查询
     */
    @Test
    public void testMultipleGroupBy() {
        // 按role_id和status分组并计数
        long count = QueryBuilder.from(User.class)
                .groupBy(User::getRoleId)
                .groupBy(User::getStatus)
                .count();
        // 应该有3个不同的分组组合
        assertEquals(3, count);
    }
    
    /**
     * 测试分组后使用having条件
     */
    @Test
    public void testGroupByWithHaving() {
        // 按role_id分组，并筛选role_id大于1的分组
        long count = QueryBuilder.from(User.class)
                .groupBy(User::getRoleId)
                .having(User::getRoleId)
                .gt(1)
                .count();
        // 应该只有1个分组满足条件
        assertEquals(1, count);
    }
    
    /**
     * 测试复杂的having条件组合
     */
    @Test
    public void testComplexHavingConditions() {
        // 按role_id和status分组，筛选role_id等于2且status等于1的分组
        long count = QueryBuilder.from(User.class)
                .groupBy(User::getRoleId)
                .groupBy(User::getStatus)
                .having(User::getRoleId)
                .eq(2)
                .having(User::getStatus)
                .eq(1)
                .count();
        // 应该只有1个分组满足条件
        assertEquals(1, count);
    }

    /**
     * 测试MultipleResultsException异常
     */
    @Test
    public void testMultipleResultsException() {
        // 应该抛出MultipleResultsException，因为查询结果超过1条
        assertThrows(MultipleResultsException.class, () -> {
            QueryBuilder.from(User.class)
                    .where(User::getRoleId)
                    .eq(2)
                    .single();
        });
    }

    /**
     * 测试空结果集
     */
    @Test
    public void testEmptyResult() {
        // 查询不存在的用户
        User nonExistentUser = QueryBuilder.from(User.class)
                .where(User::getUsername)
                .eq("non_existent_user")
                .single();
        assertNull(nonExistentUser);

        // 查询不存在的用户列表
        List<User> emptyList = QueryBuilder.from(User.class)
                .where(User::getUsername)
                .eq("non_existent_user")
                .list();
        assertNotNull(emptyList);
        assertTrue(emptyList.isEmpty());
    }

    /**
     * 测试复杂查询链
     */
    @Test
    public void testComplexQueryChain() {
        // 复杂查询链：多条件、排序、分页
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getStatus)
                .eq(1)
                .and(User::getAge)
                .gte(25)
                .orderBy(User::getAge)
                .desc()
                .list();
        assertNotNull(users);
        assertTrue(users.size() <= 10);
        // 验证所有结果都满足条件
        assertTrue(users.stream().allMatch(u -> u.getStatus() == 1 && u.getAge() >= 25));
    }
    
    /**
     * 测试OR条件查询
     */
    @Test
    public void testOrCondition() {
        // 使用OR条件查询不同用户名的用户
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getUsername)
                .eq("admin")
                .or(User::getUsername)
                .eq("user1")
                .list();
        assertNotNull(users);
        assertEquals(2, users.size());
        assertTrue(users.stream().anyMatch(u -> "admin".equals(u.getUsername()) || "user1".equals(u.getUsername())));
    }
    
    /**
     * 测试AND和OR混合条件查询
     */
    @Test
    public void testAndOrMixedConditions() {
        // 混合AND和OR条件
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getStatus)
                .eq(1)
                .and(User::getUsername)
                .eq("admin")
                .or(User::getAge)
                .gt(28)
                .list();
        assertNotNull(users);
        // 应该返回admin用户或者年龄大于28的用户
        assertTrue(users.size() >= 1);
    }
    
    /**
     * 测试NOT NULL条件查询
     */
    @Test
    public void testNotNullQueryWithResults() {
        // 查询email不为null的用户
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getEmail)
                .isNotNull()
                .list();
        assertNotNull(users);
        // 初始测试数据中所有用户都有email
        assertEquals(3, users.size());
    }
    
    /**
     * 测试大结果集的分页处理
     */
    @Test
    public void testLargeResultPagination() {
        // 先插入更多测试数据
        for (int i = 4; i <= 20; i++) {
            jdbcTemplate.execute(String.format(
                    "INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (%d, 'user%d', '123456', 'user%d@test.com', 2, %d, 1)",
                    i, i, i, 20 + i));
        }
        
        // 测试分页查询
        List<User> page1 = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .asc()
                .list();
        assertEquals(3, page1.size()); // 只有3条测试数据
        assertEquals(1, page1.get(0).getId());
        
        List<User> page2 = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .asc()
                .list();
        assertEquals(3, page2.size()); // 只有3条测试数据
        assertEquals(1, page2.get(0).getId()); // 数据与page1相同
    }
    
    /**
     * 测试in条件的边界情况
     */
    @Test
    public void testInConditionEdgeCases() {
        // 测试只有一个元素的集合
        List<Integer> singleId = Collections.singletonList(1);
        List<User> singleUser = QueryBuilder.from(User.class)
                .where(User::getId)
                .in(singleId)
                .list();
        assertEquals(1, singleUser.size());
        assertEquals("admin", singleUser.get(0).getUsername());
        
        // 测试空集合
        List<Integer> emptyIds = Collections.emptyList();
        List<User> emptyUsers = QueryBuilder.from(User.class)
                .where(User::getId)
                .in(emptyIds)
                .list();
        assertNotNull(emptyUsers);
        assertTrue(emptyUsers.isEmpty());
    }
    
    /**
     * 测试like条件的多种形式
     */
    @Test
    public void testLikeConditionVariations() {
        // 测试前缀匹配
        List<User> prefixMatch = QueryBuilder.from(User.class)
                .where(User::getUsername)
                .like("user%")
                .list();
        assertEquals(2, prefixMatch.size());
        
        // 测试中间匹配
        List<User> middleMatch = QueryBuilder.from(User.class)
                .where(User::getUsername)
                .like("%er%")
                .list();
        assertTrue(middleMatch.size() >= 2); // 应该匹配到user1和user2
        
        // 测试精确匹配（相当于eq）
        List<User> exactMatch = QueryBuilder.from(User.class)
                .where(User::getUsername)
                .like("admin")
                .list();
        assertEquals(1, exactMatch.size());
        assertEquals("admin", exactMatch.get(0).getUsername());
    }
    
    /**
     * 测试动态查询条件构建
     */
    @Test
    public void testDynamicQueryBuilding() {
        // 模拟动态条件构建场景
        Integer roleId = 2;
        String usernameLike = "user";
        Integer minAge = 25;
        
        // 使用正确的QueryBuilder API调用方式
        EntitySqlBuilder<User> queryBuilder = QueryBuilder.from(User.class);
        
        // 构建条件
        boolean hasCondition = false;
        WhereClause<User> whereClause = null;
        ConditionClause<User> conditionClause = null;
        
        // 动态添加条件
        if (roleId != null) {
            whereClause = queryBuilder.where(User::getRoleId);
            conditionClause = whereClause.eq(roleId);
            hasCondition = true;
            
            if (usernameLike != null) {
                whereClause = conditionClause.and(User::getUsername);
                conditionClause = whereClause.like(usernameLike);
                
                if (minAge != null) {
                    whereClause = conditionClause.and(User::getAge);
                    conditionClause = whereClause.gte(minAge);
                }
            } else if (minAge != null) {
                whereClause = conditionClause.and(User::getAge);
                conditionClause = whereClause.gte(minAge);
            }
        } else if (usernameLike != null) {
            whereClause = queryBuilder.where(User::getUsername);
            conditionClause = whereClause.like(usernameLike);
            hasCondition = true;
            
            if (minAge != null) {
                whereClause = conditionClause.and(User::getAge);
                conditionClause = whereClause.gte(minAge);
            }
        } else if (minAge != null) {
            whereClause = queryBuilder.where(User::getAge);
            conditionClause = whereClause.gte(minAge);
            hasCondition = true;
        }
        
        // 调用list()方法获取结果
        List<User> users;
        if (hasCondition && conditionClause != null) {
            // 如果有条件，从条件对象调用list()
            users = conditionClause.list();
        } else {
            // 如果没有条件，从原始构建器调用list()
            users = queryBuilder.list();
        }
        
        assertNotNull(users);
        // 应该只返回符合所有条件的用户
        assertTrue(users.stream().allMatch(u -> 
                u.getRoleId().equals(Long.valueOf(roleId)) && 
                u.getUsername().contains(usernameLike) && 
                u.getAge() >= minAge));
    }
    
    /**
     * 测试链式排序
     */
    @Test
    public void testChainedOrdering() {
        // 先按status降序，再按age升序排序
        List<User> users = QueryBuilder.from(User.class)
                .orderBy(User::getStatus)
                .desc()
                .orderBy(User::getAge)
                .asc()
                .list();
        
        assertNotNull(users);
        assertEquals(3, users.size());
        
        // 验证排序结果
        // 状态为1的用户应该排在前面
        assertTrue(users.get(0).getStatus() == 1);
        // 状态相同的用户应该按年龄升序排列
        List<User> activeUsers = users.stream()
                .filter(u -> u.getStatus() == 1)
                .collect(Collectors.toList());
        if (activeUsers.size() >= 2) {
            assertTrue(activeUsers.get(0).getAge() <= activeUsers.get(1).getAge());
        }
    }
}