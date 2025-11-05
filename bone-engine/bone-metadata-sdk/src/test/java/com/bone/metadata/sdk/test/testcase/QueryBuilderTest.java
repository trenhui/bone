package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.test.domain.Role;
import com.bone.metadata.sdk.test.domain.SalesRecord;
import com.bone.metadata.sdk.test.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryBuilder测试类 - 使用真实数据库进行测试
 * 基于纯Lambda版本实现，遵循业界最佳实践
 */
@SpringBootTest(classes = com.bone.metadata.sdk.test.config.TestConfig.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
public class QueryBuilderTest {

    @Autowired
    private SqlExecutor sqlExecutor;

    @BeforeEach
    void setUp() {
        // 初始化QueryBuilder

        // 初始化测试数据
        initTestData();
    }

    /**
     * 初始化测试数据
     */
    private void initTestData() {
        try {
            // 1. 清理数据
            sqlExecutor.execute("DELETE FROM sales_record WHERE 1=1");
            sqlExecutor.execute("DELETE FROM users WHERE 1=1");
            sqlExecutor.execute("DELETE FROM roles WHERE 1=1");

            // 2. 插入角色（用命名参数）
            sqlExecutor.execute(
                    "INSERT INTO roles(id, role_name, description) VALUES(:id, :roleName, :desc)",
                    Map.of("id", 1, "roleName", "管理员", "desc", "系统管理员")
            );
            sqlExecutor.execute(
                    "INSERT INTO roles(id, role_name, description) VALUES(:id, :roleName, :desc)",
                    Map.of("id", 2, "roleName", "普通用户", "desc", "普通用户角色")
            );

            // 3. 插入用户
            List.of(
                    Map.of("id", 1L, "name", "张三", "roleId", 1L),
                    Map.of("id", 2L, "name", "李四", "roleId", 2L),
                    Map.of("id", 3L, "name", "王五", "roleId", 2L),
                    Map.of("id", 4L, "name", "赵六", "roleId", 1L)
            ).forEach(params -> sqlExecutor.execute(
                    "INSERT INTO users(id, name, role_id) VALUES(:id, :name, :roleId)", params
            ));

            // 4. 插入销售记录（重点！全部改成 :createTime）
            LocalDateTime now = LocalDateTime.now();
            List<Map<String, Object>> sales = List.of(
                    Map.<String, Object>of(
                            "id", 1L, "category", "电子产品", "amount", new BigDecimal("2999.99"),
                            "price", new BigDecimal("2999.99"), "status", "已完成", "createTime", now,
                            "region", "华东", "productName", "智能手机A", "quantity", 1, "isDeleted", false
                    ),
                    Map.<String, Object>of(
                            "id", 2L, "category", "电子产品", "amount", new BigDecimal("5999.99"),
                            "price", new BigDecimal("5999.99"), "status", "已完成", "createTime", now,
                            "region", "华北", "productName", "笔记本电脑B", "quantity", 1, "isDeleted", false
                    ),
                    Map.<String, Object>of(
                            "id", 3L, "category", "家居用品", "amount", new BigDecimal("299.99"),
                            "price", new BigDecimal("299.99"), "status", "处理中", "createTime", now,
                            "region", "华南", "productName", "沙发罩C", "quantity", 2, "isDeleted", false
                    ),
                    Map.<String, Object>of(
                            "id", 4L, "category", "电子产品", "amount", new BigDecimal("1999.99"),
                            "price", new BigDecimal("1999.99"), "status", "已完成", "createTime", now,
                            "region", "华东", "productName", "平板电脑D", "quantity", 1, "isDeleted", true
                    )
            );

            String insertSalesSql = """
            INSERT INTO sales_record(
                id, category, amount, price, status, create_time, 
                region, product_name, quantity, is_deleted
            ) VALUES (
                :id, :category, :amount, :price, :status, :createTime,
                :region, :productName, :quantity, :isDeleted
            )
            """;

            sales.forEach(params -> sqlExecutor.execute(insertSalesSql, params));

        } catch (Exception e) {
            throw new RuntimeException("初始化测试数据失败: " + e.getMessage(), e);
        }
    }
    // ===== 基础功能测试 =====

    @Test
    void testFromMethod() {
        // 测试from方法是否能正确创建FluentQuery实例
        assertNotNull(QueryBuilder.from(User.class), "FluentQuery实例不应为null");
        assertNotNull(QueryBuilder.from(Role.class), "FluentQuery实例不应为null");
        assertNotNull(QueryBuilder.from(SalesRecord.class), "FluentQuery实例不应为null");
    }

    @Test
    void testBasicQueryWithWhere() {
        // 测试简单条件查询
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getName).eq("张三")
                .list();

        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("张三", users.get(0).getName());
    }

    @Test
    void testSingleResultQuery() {
        // 测试单个结果查询
        Optional<User> userOpt = QueryBuilder.from(User.class)
                .where(User::getId).eq(1L)
                .singleOpt();

        assertTrue(userOpt.isPresent());
        assertEquals(1L, userOpt.get().getId());
        assertEquals("张三", userOpt.get().getName());
    }

    @Test
    void testSingleResultNotFound() {
        // 测试查询不存在的单个结果
        Optional<User> userOpt = QueryBuilder.from(User.class)
                .where(User::getId).eq(999L)
                .singleOpt();

        assertFalse(userOpt.isPresent());
    }

    @Test
    void testFirstResultQuery() {
        // 测试获取第一个结果
        Optional<User> userOpt = QueryBuilder.from(User.class)
                .orderBy(User::getId, true)
                .first();

        assertTrue(userOpt.isPresent());
        assertEquals(1L, userOpt.get().getId());
    }

    @Test
    void testCountQuery() {
        // 测试计数查询
        long totalCount = QueryBuilder.from(User.class).count();
        assertEquals(4, totalCount);

        // 按条件计数
        long role1Count = QueryBuilder.from(User.class)
                .where(User::getRoleId).eq(1L)
                .count();

        assertEquals(2, role1Count);
    }

    @Test
    void testExistsQuery() {
        // 测试存在性检查
        boolean exists = QueryBuilder.from(User.class)
                .where(User::getName).eq("张三")
                .exists();

        assertTrue(exists);

        boolean notExists = QueryBuilder.from(User.class)
                .where(User::getName).eq("不存在的用户")
                .exists();

        assertFalse(notExists);
    }

    // ===== 排序和分页测试 =====

    @Test
    void testOrderByAsc() {
        // 测试升序排序
        List<User> users = QueryBuilder.from(User.class)
                .orderByAsc(User::getId)
                .list();

        assertNotNull(users);
        assertTrue(users.size() > 1);
        assertTrue(users.get(0).getId() < users.get(1).getId());
    }

    @Test
    void testOrderByDesc() {
        // 测试降序排序
        List<User> users = QueryBuilder.from(User.class)
                .orderByDesc(User::getId)
                .list();

        assertNotNull(users);
        assertTrue(users.size() > 1);
        assertTrue(users.get(0).getId() > users.get(1).getId());
    }

    @Test
    void testLimitAndOffset() {
        // 测试限制和偏移
        List<User> users = QueryBuilder.from(User.class)
                .orderByAsc(User::getId)
                .limit(2)
                .offset(1)
                .list();

        assertNotNull(users);
        assertEquals(2, users.size());
        assertEquals(2L, users.get(0).getId());
        assertEquals(3L, users.get(1).getId());
    }

    @Test
    void testPaginationQuery() {
        // 测试分页查询
        com.bone.core.model.PageResult<User> pageResult = QueryBuilder.from(User.class)
                .orderBy(User::getId, true)
                .page(1, 2);

        assertNotNull(pageResult);
        assertEquals(2, pageResult.getRecords().size());
        assertEquals(4, pageResult.getTotal());
        assertEquals(1, pageResult.getPage());
        assertEquals(2, pageResult.getSize());
    }

    // ===== 复杂条件测试 =====

    @Test
    void testMultipleConditions() {
        // 测试多个条件组合
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getRoleId).eq(2L)
                .where(User::getName).contains("李")
                .list();

        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("李四", users.get(0).getName());
    }

    @Test
    void testInCondition() {
        // 测试IN条件
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getId).in(Arrays.asList(1L, 2L, 3L))
                .list();

        assertNotNull(users);
        assertEquals(3, users.size());
    }

    @Test
    void testNotInCondition() {
        // 测试NOT IN条件
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getId).notIn(Arrays.asList(1L, 2L))
                .list();

        assertNotNull(users);
        assertEquals(2, users.size());
        assertTrue(users.stream().allMatch(user -> user.getId() > 2L));
    }

    @Test
    void testLikeConditions() {
        // 测试各种LIKE条件
        List<User> usersStartsWith = QueryBuilder.from(User.class)
                .where(User::getName).startsWith("张")
                .list();
        assertEquals(1, usersStartsWith.size());

        List<User> usersEndsWith = QueryBuilder.from(User.class)
                .where(User::getName).endsWith("四")
                .list();
        assertEquals(1, usersEndsWith.size());

        List<User> usersContains = QueryBuilder.from(User.class)
                .where(User::getName).contains("五")
                .list();
        assertEquals(1, usersContains.size());
    }

    @Test
    void testComparisonConditions() {
        // 测试比较条件
        List<User> usersGt = QueryBuilder.from(User.class)
                .where(User::getId).gt(2L)
                .list();
        assertEquals(2, usersGt.size());

        List<User> usersGte = QueryBuilder.from(User.class)
                .where(User::getId).gte(2L)
                .list();
        assertEquals(3, usersGte.size());

        List<User> usersLt = QueryBuilder.from(User.class)
                .where(User::getId).lt(3L)
                .list();
        assertEquals(2, usersLt.size());

        List<User> usersLte = QueryBuilder.from(User.class)
                .where(User::getId).lte(3L)
                .list();
        assertEquals(3, usersLte.size());
    }

    @Test
    void testNullConditions() {
        // 测试空值条件
        List<User> usersNotNull = QueryBuilder.from(User.class)
                .where(User::getName).isNotNull()
                .list();
        assertEquals(4, usersNotNull.size());
    }

    @Test
    void testBetweenCondition() {
        // 测试BETWEEN条件
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getId).between(2L, 3L)
                .list();

        assertNotNull(users);
        assertEquals(2, users.size());
        assertTrue(users.stream().allMatch(user -> user.getId() >= 2L && user.getId() <= 3L));
    }

    // ===== 关联查询测试 =====

    @Test
    void testJoinQuery() {
        // 测试关联查询
        List<User> users = QueryBuilder.from(User.class)
                .joinOn(Role.class, "r", User::getRoleId, Role::getId)
                .where(User::getName).eq("张三")
                .list();

        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("张三", users.get(0).getName());
    }

    @Test
    void testLeftJoinQuery() {
        // 测试左关联查询
        List<User> users = QueryBuilder.from(User.class)
                .leftJoinOn(Role.class, "r", User::getRoleId, Role::getId)
                .list();

        assertNotNull(users);
        assertEquals(4, users.size());
    }

    // ===== 分组和聚合测试 =====

    @Test
    void testGroupBy() {
        // 测试分组查询
        List<User> users = QueryBuilder.from(User.class)
                .groupBy(User::getRoleId)
                .list();

        assertNotNull(users);
        // 分组后应该返回每个组的代表记录
    }

    @Test
    void testAggregateFunctions() {
        // 测试聚合函数
        Optional<Long> userCount = QueryBuilder.from(User.class)
                .aggregate("COUNT", User::getId, Long.class);

        assertTrue(userCount.isPresent());
        assertEquals(4L, userCount.get());

        // 测试带条件的聚合
        Optional<Long> role1Count = QueryBuilder.from(User.class)
                .where(User::getRoleId).eq(1L)
                .aggregate("COUNT", User::getId, Long.class);

        assertTrue(role1Count.isPresent());
        assertEquals(2L, role1Count.get());
    }

    // ===== SalesRecord实体特定测试 =====

    @Test
    void testSalesRecordBasicQuery() {
        // 测试SalesRecord基本查询
        List<SalesRecord> records = QueryBuilder.from(SalesRecord.class)
                .where(SalesRecord::getCategory).eq("电子产品")
                .list();

        assertNotNull(records);
        assertEquals(3, records.size()); // 包含已删除的记录
        assertTrue(records.stream().allMatch(record -> "电子产品".equals(record.getCategory())));
    }

    @Test
    void testSalesRecordBooleanCondition() {
        // 测试布尔条件查询
        List<SalesRecord> activeRecords = QueryBuilder.from(SalesRecord.class)
                .where(SalesRecord::getIsDeleted).eq(false)
                .list();

        assertEquals(3, activeRecords.size());

        List<SalesRecord> deletedRecords = QueryBuilder.from(SalesRecord.class)
                .where(SalesRecord::getIsDeleted).eq(true)
                .list();

        assertEquals(1, deletedRecords.size());
    }

    @Test
    void testSalesRecordRangeQuery() {
        // 测试范围查询
        List<SalesRecord> records = QueryBuilder.from(SalesRecord.class)
                .where(SalesRecord::getAmount).gt(new BigDecimal("1000"))
                .where(SalesRecord::getAmount).lt(new BigDecimal("6000"))
                .list();

        assertNotNull(records);
        assertTrue(records.stream().allMatch(record ->
                record.getAmount().compareTo(new BigDecimal("1000")) > 0 &&
                        record.getAmount().compareTo(new BigDecimal("6000")) < 0
        ));
    }

    @Test
    void testSalesRecordComplexQuery() {
        // 测试复杂查询组合
        List<SalesRecord> records = QueryBuilder.from(SalesRecord.class)
                .where(SalesRecord::getCategory).eq("电子产品")
                .where(SalesRecord::getStatus).eq("已完成")
                .where(SalesRecord::getIsDeleted).eq(false)
                .orderByDesc(SalesRecord::getAmount)
                .list();

        assertNotNull(records);
        assertEquals(2, records.size());
        assertTrue(records.get(0).getAmount().compareTo(records.get(1).getAmount()) > 0);
    }

    // ===== 高级功能测试 =====

    @Test
    void testSelectProjection() {
        // 测试字段投影
        List<String> userNames = QueryBuilder.from(User.class)
                .select(User::getName, String.class);

        assertNotNull(userNames);
        assertEquals(4, userNames.size());
        assertTrue(userNames.contains("张三"));
        assertTrue(userNames.contains("李四"));
        assertTrue(userNames.contains("王五"));
        assertTrue(userNames.contains("赵六"));
    }

    @Test
    void testMapFunction() {
        // 测试映射功能
        List<String> userNames = QueryBuilder.from(User.class)
                .map(User::getName);

        assertNotNull(userNames);
        assertEquals(4, userNames.size());
    }

    @Test
    void testForEach() {
        // 测试遍历功能
        QueryBuilder.from(User.class)
                .forEach(user -> {
                    assertNotNull(user);
                    assertNotNull(user.getName());
                });
    }

    @Test
    void testStream() {
        // 测试流式处理
        long count = QueryBuilder.from(User.class)
                .stream()
                .filter(user -> user.getRoleId() == 1L)
                .count();

        assertEquals(2, count);
    }

    @Test
    void testComplexConditionWithAndOr() {
        // 正确的使用方式：通过Condition的and/or方法返回FluentQuery后继续链式调用
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getRoleId).eq(1L)
                .or(User::getName).contains("李")
                .list();

        assertNotNull(users);
        assertEquals(3, users.size());
    }

    @Test
    void testComplexWhereBuilder() {
        // 测试复杂条件构建器 - 正确的使用方式
        List<User> users = QueryBuilder.from(User.class)
                .where(builder -> {
                    // 每个条件独立调用
                    builder.and(User::getRoleId).eq(1L);
                    builder.or(User::getName).contains("王");
                })
                .list();

        assertNotNull(users);
        // 预期结果：角色ID为1的用户 或者 名字包含"王"的用户
        // 2个管理员(张三、赵六) + 王五 = 3个用户
        assertEquals(3, users.size());
    }

    // ===== 异常情况测试 =====

    @Test
    void testNonUniqueResultException() {
        // 测试非唯一结果异常
        assertThrows(Exception.class, () -> {
            QueryBuilder.from(User.class)
                    .where(User::getRoleId).eq(2L)
                    .single();
        });
    }

    @Test
    void testEmptyResult() {
        // 测试空结果
        List<User> users = QueryBuilder.from(User.class)
                .where(User::getName).eq("不存在的用户")
                .list();

        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    // ===== 性能测试 =====

    @Test
    void testQueryPerformance() {
        // 简单的性能测试
        long startTime = System.currentTimeMillis();

        List<User> users = QueryBuilder.from(User.class).list();
        List<Role> roles = QueryBuilder.from(Role.class).list();
        List<SalesRecord> sales = QueryBuilder.from(SalesRecord.class).list();

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        // 验证查询结果
        assertNotNull(users);
        assertNotNull(roles);
        assertNotNull(sales);

        // 记录性能信息
        System.out.println("批量查询执行时间: " + executionTime + "ms");
        assertTrue(executionTime < 5000, "查询应在5秒内完成");
    }

    /**
     * 测试清理
     */
    @AfterEach
    void tearDown() {
        // 由于使用了@Transactional注解，事务会自动回滚
        // 这里保留清理逻辑作为额外保障
        try {
            sqlExecutor.execute("DELETE FROM sales_record WHERE 1=1");
            sqlExecutor.execute("DELETE FROM users WHERE 1=1");
            sqlExecutor.execute("DELETE FROM roles WHERE 1=1");
        } catch (Exception e) {
            System.err.println("清理测试数据失败: " + e.getMessage());
        }
    }
}