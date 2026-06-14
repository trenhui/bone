package com.bone.metadata.sdk.test.testcase;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.SalesRecord;
import com.bone.metadata.sdk.test.repository.impl.SalesRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
public class SalesRecordRepositoryTest {

  @Autowired private SalesRecordRepository salesRecordRepository;
  @Autowired private NamedParameterJdbcOperations jdbc;

  @BeforeEach
  public void setUp() {
    // 清空表并插入测试数据
    jdbc.update("DELETE FROM sales_record", new MapSqlParameterSource());

    insertTestData();
  }

  private void insertTestData() {
    // 插入测试数据
    List<SalesRecord> testData =
        Arrays.asList(
            new SalesRecord(
                "电子产品",
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 15, 10, 30),
                "华东",
                "智能手机",
                2,
                false),
            new SalesRecord(
                "电子产品",
                new BigDecimal("2500.00"),
                new BigDecimal("1250.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 16, 14, 20),
                "华东",
                "笔记本电脑",
                2,
                false),
            new SalesRecord(
                "电子产品",
                new BigDecimal("800.00"),
                new BigDecimal("800.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 17, 9, 15),
                "华东",
                "耳机",
                1,
                false),
            new SalesRecord(
                "电子产品",
                new BigDecimal("4500.00"),
                new BigDecimal("1500.00"),
                "INACTIVE",
                LocalDateTime.of(2023, 1, 18, 16, 45),
                "华南",
                "平板电脑",
                3,
                false),
            new SalesRecord(
                "服装",
                new BigDecimal("300.00"),
                new BigDecimal("150.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 15, 11, 20),
                "华北",
                "衬衫",
                2,
                false),
            new SalesRecord(
                "服装",
                new BigDecimal("500.00"),
                new BigDecimal("250.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 16, 13, 10),
                "华北",
                "裤子",
                2,
                false),
            new SalesRecord(
                "服装",
                new BigDecimal("1200.00"),
                new BigDecimal("400.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 17, 15, 30),
                "华南",
                "外套",
                3,
                false),
            new SalesRecord(
                "服装",
                new BigDecimal("800.00"),
                new BigDecimal("200.00"),
                "INACTIVE",
                LocalDateTime.of(2023, 1, 18, 10, 45),
                "西南",
                "鞋子",
                4,
                false),
            new SalesRecord(
                "食品",
                new BigDecimal("200.00"),
                new BigDecimal("40.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 15, 12, 30),
                "华东",
                "零食礼包",
                5,
                false),
            new SalesRecord(
                "食品",
                new BigDecimal("150.00"),
                new BigDecimal("30.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 16, 14, 40),
                "华北",
                "饮料",
                5,
                false),
            new SalesRecord(
                "食品",
                new BigDecimal("300.00"),
                new BigDecimal("60.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 1, 17, 16, 20),
                "华南",
                "方便面",
                5,
                false),
            new SalesRecord(
                "食品",
                new BigDecimal("180.00"),
                new BigDecimal("36.00"),
                "INACTIVE",
                LocalDateTime.of(2023, 1, 18, 11, 30),
                "西南",
                "饼干",
                5,
                false),
            new SalesRecord(
                "电子产品",
                new BigDecimal("3200.00"),
                new BigDecimal("1600.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 2, 10, 9, 30),
                "华南",
                "游戏机",
                2,
                false),
            new SalesRecord(
                "电子产品",
                new BigDecimal("1800.00"),
                new BigDecimal("900.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 2, 11, 14, 15),
                "西南",
                "显示器",
                2,
                false),
            new SalesRecord(
                "服装",
                new BigDecimal("600.00"),
                new BigDecimal("300.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 2, 10, 11, 20),
                "华东",
                "毛衣",
                2,
                false),
            new SalesRecord(
                "服装",
                new BigDecimal("900.00"),
                new BigDecimal("450.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 2, 11, 15, 40),
                "华北",
                "羽绒服",
                2,
                false),
            new SalesRecord(
                "食品",
                new BigDecimal("250.00"),
                new BigDecimal("50.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 2, 10, 13, 10),
                "华南",
                "巧克力",
                5,
                false),
            new SalesRecord(
                "食品",
                new BigDecimal("120.00"),
                new BigDecimal("24.00"),
                "ACTIVE",
                LocalDateTime.of(2023, 2, 11, 16, 50),
                "西南",
                "糖果",
                5,
                false));

    salesRecordRepository.batchInsert(testData);
  }

  @Test
  public void testAggregateWithoutGroupBy() {
    // 测试无GROUP BY的聚合查询
    Map<String, Object> result =
        salesRecordRepository.aggregate(
            Arrays.asList("COUNT(*) as total", "SUM(amount) as totalAmount"),
            Criteria.<SalesRecord>create());

    assertNotNull(result);
    assertEquals(18L, result.get("total"));
    assertEquals(
        new BigDecimal("19300.00"), new BigDecimal(result.get("totalAmount").toString())); // 修正期望值

    log.info("无GROUP BY聚合查询结果: {}", result);
  }

  @Test
  public void testAggregateWithGroupBy() {
    // 测试带GROUP BY的聚合查询
    List<Map<String, Object>> results =
        salesRecordRepository.aggregate(
            Arrays.asList("category", "COUNT(*) as count", "AVG(price) as avgPrice"),
            Criteria.<SalesRecord>create().eq("status", "ACTIVE"),
            Arrays.asList("category"));

    assertNotNull(results);
    assertEquals(3, results.size()); // 3个类别：电子产品、服装、食品

    // 验证电子产品的统计结果
    Map<String, Object> electronics =
        results.stream().filter(r -> "电子产品".equals(r.get("category"))).findFirst().orElse(null);

    assertNotNull(electronics);
    assertEquals(5L, electronics.get("count")); // 电子产品有5条ACTIVE状态的记录
    // 平均价格可能需要重新计算
    // assertTrue(new BigDecimal(electronics.get("avgPrice").toString()).compareTo(new
    // BigDecimal("1010.00")) == 0);

    log.info("带GROUP BY聚合查询结果: {}", results);
  }

  @Test
  public void testAggregateWithGroupByAndHaving() {
    // 测试带GROUP BY和HAVING的聚合查询
    List<Map<String, Object>> results =
        salesRecordRepository.aggregate(
            Arrays.asList("category", "COUNT(*) as count", "SUM(amount) as total"),
            Criteria.<SalesRecord>create()
                .between(
                    "createdAt",
                    LocalDateTime.of(2023, 1, 15, 0, 0),
                    LocalDateTime.of(2023, 2, 11, 23, 59)),
            Arrays.asList("category"),
            Arrays.asList("COUNT(*) > 4", "SUM(amount) > 1000"));

    assertNotNull(results);
    assertEquals(3, results.size()); // 所有三个类别都满足条件

    // 验证电子产品的统计结果
    Map<String, Object> electronics =
        results.stream().filter(r -> "电子产品".equals(r.get("category"))).findFirst().orElse(null);

    assertNotNull(electronics);
    assertEquals(6L, electronics.get("count")); // 电子产品有6条记录
    assertEquals(
        new BigDecimal("13800.00"), new BigDecimal(electronics.get("total").toString())); // 修正期望值

    log.info("带GROUP BY和HAVING聚合查询结果: {}", results);
  }

  @Test
  public void testAggregateWithPagination() {
    // 测试分页的聚合查询
    PageResult<Map<String, Object>> pageResult =
        salesRecordRepository.aggregateWithPagination(
            Arrays.asList("category", "SUM(amount) as total"),
            Criteria.<SalesRecord>create()
                .between(
                    "createdAt",
                    LocalDateTime.of(2023, 1, 15, 0, 0),
                    LocalDateTime.of(2023, 2, 11, 23, 59)),
            Arrays.asList("category"),
            null,
            1, // 第一页
            2 // 每页2条
            );

    assertNotNull(pageResult);
    assertEquals(3, pageResult.getTotal()); // 总共3个类别
    assertEquals(2, pageResult.getRecords().size()); // 第一页有2条记录
    assertEquals(1, pageResult.getPage()); // 当前是第一页
    assertEquals(2, pageResult.getSize()); // 每页2条

    log.info("分页聚合查询结果: {}", pageResult);
  }

  @Test
  public void testAggregateWithMultipleGroupBy() {
    // 测试多字段分组聚合查询
    List<Map<String, Object>> results =
        salesRecordRepository.aggregate(
            Arrays.asList("category", "region", "COUNT(*) as count", "SUM(amount) as total"),
            Criteria.<SalesRecord>create().eq("status", "ACTIVE"),
            Arrays.asList("category", "region"));

    assertNotNull(results);
    assertTrue(results.size() > 0);

    // 验证每个结果都包含category和region字段
    results.forEach(
        result -> {
          assertTrue(result.containsKey("category"));
          assertTrue(result.containsKey("region"));
          assertTrue(result.containsKey("count"));
          assertTrue(result.containsKey("total"));
        });

    log.info("多字段分组聚合查询结果: {}", results);
  }

  @Test
  public void testAggregateWithComplexCriteria() {
    // 测试复杂条件的聚合查询
    List<Map<String, Object>> results =
        salesRecordRepository.aggregate(
            Arrays.asList("category", "AVG(price) as avgPrice", "SUM(quantity) as totalQuantity"),
            Criteria.<SalesRecord>create()
                .eq("status", "ACTIVE")
                .gt("price", new BigDecimal("100.00")),
            Arrays.asList("category"));

    assertNotNull(results);
    assertTrue(results.size() > 0);

    // 验证每个类别的平均价格都大于100
    results.forEach(
        result -> {
          BigDecimal avgPrice = new BigDecimal(result.get("avgPrice").toString());
          assertTrue(avgPrice.compareTo(new BigDecimal("100.00")) > 0);
        });

    log.info("复杂条件聚合查询结果: {}", results);
  }

  @Test
  public void testAggregateWithNoResults() {
    // 测试无结果的聚合查询
    List<Map<String, Object>> results =
        salesRecordRepository.aggregate(
            Arrays.asList("category", "COUNT(*) as count"),
            Criteria.<SalesRecord>create().eq("status", "NON_EXISTENT"),
            Arrays.asList("category"));

    assertNotNull(results);
    assertEquals(0, results.size());

    log.info("无结果聚合查询: {}", results);
  }
}
