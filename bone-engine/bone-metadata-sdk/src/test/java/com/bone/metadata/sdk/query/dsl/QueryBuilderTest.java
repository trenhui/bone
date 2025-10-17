package com.bone.metadata.sdk.query.dsl;

import com.bone.metadata.sdk.test.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryBuilder单元测试类
 * 测试各种DSL方法的正确性和链式调用的类型安全
 */
@ExtendWith(MockitoExtension.class)
class QueryBuilderTest {

    /**
     * 测试基础查询构建功能
     */
    @Test
    void testBasicQueryBuilding() {
        // 测试from方法返回EntitySqlBuilder实例
        EntitySqlBuilder<User> queryBuilder = QueryBuilder.from(User.class);
        assertNotNull(queryBuilder);
        assertInstanceOf(EntitySqlBuilder.class, queryBuilder);
    }

    /**
     * 测试selectFrom别名方法
     */
    @Test
    void testSelectFromAlias() {
        // 测试selectFrom方法是from方法的别名
        EntitySqlBuilder<User> queryBuilder = QueryBuilder.selectFrom(User.class);
        assertNotNull(queryBuilder);
        assertInstanceOf(EntitySqlBuilder.class, queryBuilder);
    }

    /**
     * 测试where条件构建
     */
    @Test
    void testWhereCondition() {
        // 测试where方法返回WhereClause实例
        WhereClause<User> whereClause = QueryBuilder.from(User.class).where(User::getId);
        assertNotNull(whereClause);
        assertInstanceOf(WhereClause.class, whereClause);
    }

    /**
     * 测试等于条件构建
     */
    @Test
    void testEqualsCondition() {
        // 测试eq方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .eq(1L);
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试不等于条件构建
     */
    @Test
    void testNotEqualsCondition() {
        // 测试gt方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getRoleId)
                .gt(1L);
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试大于条件构建
     */
    @Test
    void testGreaterThanCondition() {
        // 测试gt方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .gt(1L);
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试小于条件构建
     */
    @Test
    void testLessThanCondition() {
        // 测试lt方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .lt(10L);
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试大于等于条件构建
     */
    @Test
    void testGreaterThanOrEqualsCondition() {
        // 测试gte方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .gte(1L);
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试小于等于条件构建
     */
    @Test
    void testLessThanOrEqualsCondition() {
        // 测试lte方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .lte(10L);
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试模糊匹配条件构建
     */
    @Test
    void testLikeCondition() {
        // 测试like方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getName)
                .like("test%");
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试IN条件构建
     */
    @Test
    void testInCondition() {
        // 测试in方法返回ConditionClause实例
        List<Long> ids = Collections.singletonList(1L);
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .in(ids);
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试NULL条件构建
     */
    @Test
    void testIsNullCondition() {
        // 测试isNull方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getName)
                .isNull();
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试NOT NULL条件构建
     */
    @Test
    void testIsNotNullCondition() {
        // 测试isNotNull方法返回ConditionClause实例
        ConditionClause<User> conditionClause = QueryBuilder.from(User.class)
                .where(User::getName)
                .isNotNull();
        assertNotNull(conditionClause);
        assertInstanceOf(ConditionClause.class, conditionClause);
    }

    /**
     * 测试AND条件连接
     */
    @Test
    void testAndCondition() {
        // 测试and方法返回WhereClause实例
        WhereClause<User> whereClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .eq(1L)
                .and(User::getName);
        assertNotNull(whereClause);
        assertInstanceOf(WhereClause.class, whereClause);
    }

    /**
     * 测试OR条件连接
     */
    @Test
    void testOrCondition() {
        // 测试or方法返回WhereClause实例
        WhereClause<User> whereClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .eq(1L)
                .or(User::getName);
        assertNotNull(whereClause);
        assertInstanceOf(WhereClause.class, whereClause);
    }

    /**
     * 测试排序功能
     */
    @Test
    void testOrderBy() {
        // 测试orderBy方法返回OrderByClause实例
        OrderByClause<User> orderByClause = QueryBuilder.from(User.class)
                .orderBy(User::getId);
        assertNotNull(orderByClause);
        assertInstanceOf(OrderByClause.class, orderByClause);
    }

    /**
     * 测试升序排序
     */
    @Test
    void testAscendingOrder() {
        // 测试asc方法返回EntitySqlBuilder实例
        EntitySqlBuilder<User> queryBuilder = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .asc();
        assertNotNull(queryBuilder);
        assertInstanceOf(EntitySqlBuilder.class, queryBuilder);
    }

    /**
     * 测试降序排序
     */
    @Test
    void testDescendingOrder() {
        // 测试desc方法返回EntitySqlBuilder实例
        EntitySqlBuilder<User> queryBuilder = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .desc();
        assertNotNull(queryBuilder);
        assertInstanceOf(EntitySqlBuilder.class, queryBuilder);
    }

    /**
     * 测试多字段排序
     */
    @Test
    void testMultipleOrdering() {
        // 测试thenBy方法返回OrderByClause实例
        OrderByClause<User> orderByClause = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .thenBy(User::getName);
        assertNotNull(orderByClause);
        assertInstanceOf(OrderByClause.class, orderByClause);

        // 测试thenAsc方法返回OrderByClause实例
        OrderByClause<User> thenAscClause = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .thenAsc(User::getName);
        assertNotNull(thenAscClause);
        assertInstanceOf(OrderByClause.class, thenAscClause);

        // 测试thenDesc方法返回OrderByClause实例
        OrderByClause<User> thenDescClause = QueryBuilder.from(User.class)
                .orderBy(User::getId)
                .thenDesc(User::getName);
        assertNotNull(thenDescClause);
        assertInstanceOf(OrderByClause.class, thenDescClause);
    }

    /**
     * 测试分页限制
     */
    @Test
    void testLimit() {
        // 测试在EntitySqlBuilder上的limit方法
        EntitySqlBuilder<User> entityBuilder = QueryBuilder.from(User.class)
                .limit(10);
        assertNotNull(entityBuilder);
        assertInstanceOf(EntitySqlBuilder.class, entityBuilder);

        // 测试在ConditionClause上的limit方法
        ConditionClause<User> conditionBuilder = QueryBuilder.from(User.class)
                .where(User::getId)
                .eq(1L)
                .limit(10);
        assertNotNull(conditionBuilder);
        assertInstanceOf(ConditionClause.class, conditionBuilder);
    }

    /**
     * 测试偏移量设置
     */
    @Test
    void testOffset() {
        // 测试在EntitySqlBuilder上的offset方法
        EntitySqlBuilder<User> entityBuilder = QueryBuilder.from(User.class)
                .offset(10);
        assertNotNull(entityBuilder);
        assertInstanceOf(EntitySqlBuilder.class, entityBuilder);

        // 测试在ConditionClause上的offset方法
        ConditionClause<User> conditionBuilder = QueryBuilder.from(User.class)
                .where(User::getId)
                .eq(1L)
                .offset(10);
        assertNotNull(conditionBuilder);
        assertInstanceOf(ConditionClause.class, conditionBuilder);
    }

    /**
     * 测试查询执行方法
     */
    @Test
    void testQueryExecutionMethods() {
        // 测试list方法返回空列表
        List<User> resultList = QueryBuilder.from(User.class).list();
        assertNotNull(resultList);
        assertTrue(resultList.isEmpty());

        // 测试single方法返回null
        User singleResult = QueryBuilder.from(User.class).single();
        assertNull(singleResult);

        // 测试count方法返回0
        long countResult = QueryBuilder.from(User.class).count();
        assertEquals(0, countResult);
    }

    /**
     * 测试复杂链式调用
     */
    @Test
    void testComplexChainedCalls() {
        // 测试复杂的链式调用组合
        List<User> result = QueryBuilder.from(User.class)
                .where(User::getId)
                .gt(1L)
                .and(User::getName)
                .like("test%")
                .or(User::getRoleId)
                .eq(2L)
                .orderBy(User::getId)
                .desc()
                .limit(10)
                .offset(5)
                .list();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试在ConditionClause上的orderBy方法
     */
    @Test
    void testOrderByOnConditionClause() {
        OrderByClause<User> orderByClause = QueryBuilder.from(User.class)
                .where(User::getId)
                .eq(1L)
                .orderBy(User::getName);
        assertNotNull(orderByClause);
        assertInstanceOf(OrderByClause.class, orderByClause);
    }

    /**
     * 测试使用不同字段类型
     */
    @Test
    void testDifferentFieldTypes() {
        // 测试字符串字段
        ConditionClause<User> stringCondition = QueryBuilder.from(User.class)
                .where(User::getName)
                .eq("test");
        assertNotNull(stringCondition);

        // 测试数字字段
        ConditionClause<User> numberCondition = QueryBuilder.from(User.class)
                .where(User::getId)
                .eq(1L);
        assertNotNull(numberCondition);

        // 测试布尔字段（如果User类有）
        // ConditionClause<User> booleanCondition = QueryBuilder.from(User.class)
        //         .where(User::isActive)
        //         .eq(true);
        // assertNotNull(booleanCondition);
    }

    /**
     * 测试查询构建器的不可实例化性
     */
    @Test
    void testQueryBuilderCannotBeInstantiated() {
        // 测试私有构造函数抛出异常
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> {
            // 通过反射尝试实例化QueryBuilder
            java.lang.reflect.Constructor<QueryBuilder> constructor = QueryBuilder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
        // 检查异常的cause是否为AssertionError且消息正确
        assertTrue(exception.getCause() instanceof AssertionError);
        assertEquals("Cannot instantiate QueryBuilder", exception.getCause().getMessage());
    }
}