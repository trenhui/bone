package com.bone.metadata.sdk.test.testcase;

import com.bone.core.tenant.context.BizIdentityContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.enums.DataType;
import com.bone.metadata.sdk.domain.exception.FieldAllocationException;
import com.bone.metadata.sdk.domain.exception.UndefinedFieldException;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.extension.ColumnAllocator;
import com.bone.metadata.sdk.extension.repository.FieldMetadataRepository;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.DataPermission;
import com.bone.metadata.sdk.test.domain.Permission;
import com.bone.metadata.sdk.test.repository.impl.PermissionRepository;
import com.bone.metadata.sdk.test.utils.TestDataHelper;

@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
public class ExtensionPermissionRepositoryTest {

    @Autowired
    private NamedParameterJdbcOperations jdbc;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private FieldMetadataRepository fieldMetadataRepository;

    @Autowired
    private ColumnAllocator columnAllocator;

    @Autowired
    private RedissonClient redissonClient; // 注入Redisson客户端


    @Autowired
    private MetadataService metadataService;

    private static final String SEQ_PREFIX = "col_seq:";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(100L);
        cleanDatabase();
        TestDataHelper.setUpPermissionTestData(jdbc);
    }

    private void cleanDatabase() {
        jdbc.getJdbcOperations().execute("DELETE FROM sys_permission");
        jdbc.getJdbcOperations().execute("DELETE FROM field_metadata");
        jdbc.getJdbcOperations().execute("DELETE FROM column_allocation");
        jdbc.getJdbcOperations().execute("DELETE FROM ext_data_reserved");
    }

    // 创建基础权限对象模板
    private DataPermission createBasePermission(String permName) {
        DataPermission perm = new DataPermission();
        perm.setPermName(permName);
        perm.setPermCode(permName + "_CODE");
        perm.setBizIdentityCode("bone");
        perm.setPermType(1); // 默认类型为菜单
        perm.setParentId(0L); // 根节点
        perm.setSortOrder(99); // 测试排序号
        return perm;
    }

    // 创建字段元数据
    private FieldMetadata createFieldMetadata(String entityType, String fieldName, DataType dataType) {
        FieldMetadata metadata = new FieldMetadata();
        metadata.setId(DistributedIdGenerator.generateLongId());
        metadata.setTenantId(TenantContext.getTenantId());
        metadata.setAppCode("extTest");
        metadata.setBizIdentityCode("bone");
        metadata.setEntityType(entityType);
        metadata.setName(fieldName);
        metadata.setDataType(dataType.name());
        metadata.setExtension(true);
        metadata.setCreatedBy(10L);
        metadata.setUpdatedBy(10L);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setUpdatedAt(LocalDateTime.now());
        return metadata;
    }

    // 清理测试数据
    private void cleanUpTestData(Long permissionId, List<FieldMetadata> fields) {
        // 删除权限记录（使用命名参数）
        jdbc.update(
                "DELETE FROM sys_permission WHERE id = :id",
                Map.of("id", permissionId)
        );

        // 删除字段元数据（使用IN子句）
        List<Long> fieldIds = fields.stream()
                .map(FieldMetadata::getId)
                .collect(Collectors.toList());

        jdbc.update(
                "DELETE FROM field_metadata WHERE id IN (:ids)",
                Map.of("ids", fieldIds)
        );
    }


    @Test
    void testContinuousAllocationWithoutExceedingLimit() {
        // 准备测试环境
        String entityType = "ContinuousTest";
        AllocationContext ctx = new AllocationContext(
                TenantContext.getTenantId(),
                "extTest",
                "bone",
                entityType
        );
        DataType dataType = DataType.STRING;
        int maxLimit = 20; // STRING类型限制

        // 多次分配直到接近极限
        AtomicInteger allocatedCount = new AtomicInteger(0);
        for (int i = 0; i < maxLimit; i++) {
            List<FieldMetadata> field = List.of(
                    createFieldMetadata(entityType, "field" + i, dataType)
            );
            assertDoesNotThrow(() -> {
                metadataService.allocateAndPersistFields(field);
                allocatedCount.incrementAndGet();
            }, "第" + (i+1) + "次分配不应出错");
        }

        assertEquals(maxLimit, allocatedCount.get(), "应成功分配全部可用字段");

        // 尝试超限分配
        FieldMetadata extraField = createFieldMetadata(entityType, "extraField", dataType);
        FieldAllocationException ex = assertThrows(FieldAllocationException.class, () -> {
            metadataService.allocateAndPersistFields(List.of(extraField));
        });
        assertTrue(ex != null);
    }


    @Test
    void testSaveAndLoadExtensionFields_ShouldPersistExtraProperties() {
        TenantContext.setTenantId(100L);
        String entityType = "Permission";

        // 预注册字段元数据
        List<FieldMetadata> fieldDefinitions = Arrays.asList(
                createFieldMetadata(entityType, "securityLevel", DataType.INTEGER),
                createFieldMetadata(entityType, "approvalRequired", DataType.BOOLEAN)
        );
        List<FieldMetadata> r = metadataService.allocateAndPersistFields(fieldDefinitions);

        DataPermission perm = createBasePermission("EXT_FIELD_TEST");
        perm.putExtraProperty("securityLevel", 5L);
        perm.putExtraProperty("approvalRequired", true);

        Long id = permissionRepository.insert(perm);
        Permission retrieved = permissionRepository.findById(id);

        assertInstanceOf(Permission.class, retrieved);
        assertEquals(5, retrieved.getExtraProperty("securityLevel", Long.class).orElse(0L));
        assertTrue(retrieved.getExtraProperty("approvalRequired", Boolean.class).orElse(false));

        // 验证数据库存储
        //Map<String, Object> dbRecord = jdbc.queryForMap("SELECT * FROM sys_permission WHERE id =:id", Map.of("id", id));

        Map<String, Object> dbRecord = jdbc.queryForMap(
                "SELECT * FROM ext_data_reserved WHERE entity_id = :entity_id and tenant_id=:tenant_id " +
                        " and app_code=:app_code and biz_identity_code=:biz_identity_code and entity_type=:entity_type" +
                        " and deleted=false",
                Map.of(
                        "tenant_id", TenantContext.getTenantId(),
                        "app_code", "extTest",
                        "biz_identity_code", "bone",
                        "entity_type", entityType,
                        "entity_id", id
                )
        );

        assertNotNull(dbRecord.get("ext_int_01"));
        assertNotNull(dbRecord.get("ext_boolean_01"));

        cleanUpTestData(id, fieldDefinitions);
    }

    @Test
    void testBatchSaveWithExtensionFields_ShouldHandleMixedOperations() {
        TenantContext.setTenantId(100L);
        String entityType = "Permission";

        // 预注册字段元数据
        List<FieldMetadata> fieldDefinitions = Arrays.asList(
                createFieldMetadata(entityType, "auditFlag", DataType.STRING),
                createFieldMetadata(entityType, "autoExpire", DataType.INTEGER)
        );
        List<FieldMetadata> result = metadataService.allocateAndPersistFields(fieldDefinitions);

        // 准备现有数据
        DataPermission existing = createBasePermission("EXISTING_PERM");
        existing.putExtraProperty("auditFlag", "ENABLED");
        Long existingId = permissionRepository.insert(existing);

        // 创建新权限对象
        DataPermission newPerm = createBasePermission("NEW_PERM");
        newPerm.putExtraProperty("autoExpire", 3600);

        // 执行批量保存
        permissionRepository.batchSave(Arrays.asList(existing, newPerm));

        // 验证更新后的数据
        Permission updated = permissionRepository.findById(existingId);
        assertEquals("ENABLED", updated.getExtraProperty("auditFlag"));

        // 验证新插入数据
        List<Permission> results = permissionRepository.findByCriteria(
                Criteria.<Permission>create().eq("permName", "NEW_PERM")
        );
        assertEquals(1, results.size());
        assertEquals(3600, (results.get(0)).getExtraProperty("autoExpire", Long.class).orElse(0L));

        cleanUpTestData(existingId, fieldDefinitions);
        Long newPermId = results.get(0).getId();
        cleanUpTestData(newPermId, fieldDefinitions);
    }

    @Test
    void testColumnExhaustion_ShouldFallbackToJsonStorage() {
        TenantContext.setTenantId(100L);
        String entityType = "Permission";

        // 1. 预注册19个字段（离上限差1个）
        List<FieldMetadata> fieldDefinitions = IntStream.range(0, 19)
                .mapToObj(i -> createFieldMetadata(entityType, "preRegField" + i, DataType.STRING))
                .collect(Collectors.toList());

        metadataService.allocateAndPersistFields(fieldDefinitions);

        // 2. 创建包含150个扩展字段的权限对象
        DataPermission perm = createBasePermission("COL_OVERFLOW_TEST");
        for (int i = 0; i < 150; i++) {
            perm.putExtraProperty("dynamicField" + i, "value" + i);
        }

        // 3. 保存实体时应触发JSON回退
        Long id = permissionRepository.insert(perm);
        Permission retrieved = permissionRepository.findById(id);

        // 验证回退机制
        assertTrue(retrieved.getExtraProperties().size() > 100, "应触发JSON回退存储");
        assertEquals("value120", retrieved.getExtraProperty("dynamicField120"));

        // 4. 清理测试数据
        cleanUpTestData(id, fieldDefinitions);
    }


    @Test
    void testExtensionFieldWithDifferentDataTypes_ShouldHandleAppropriately() {
        TenantContext.setTenantId(100L);
        String entityType = "Permission";

        // 预注册字段元数据
        List<FieldMetadata> fieldDefinitions = Arrays.asList(
                createFieldMetadata(entityType, "stringVal", DataType.STRING),
                createFieldMetadata(entityType, "intVal", DataType.INTEGER),
                createFieldMetadata(entityType, "boolVal", DataType.BOOLEAN),
                createFieldMetadata(entityType, "dateVal", DataType.DATE)
        );
        metadataService.allocateAndPersistFields(fieldDefinitions);

        DataPermission perm = createBasePermission("DATA_TYPE_TEST");
        perm.putExtraProperty("stringVal", "testString");
        perm.putExtraProperty("intVal", 2023);
        perm.putExtraProperty("boolVal", true);
        perm.putExtraProperty("dateVal", LocalDateTime.of(2023, 10, 1, 0, 0));

        Long id = permissionRepository.insert(perm);
        Permission retrieved = permissionRepository.findById(id);

        Map<String, Object> extras = retrieved.getExtraProperties();

        assertEquals("testString", extras.get("stringVal"));
        assertEquals(2023L, extras.get("intVal"));
        assertEquals(true, extras.get("boolVal"));

        // 增强的日期断言
        Object dateVal = extras.get("dateVal");
        assertNotNull(dateVal);

        LocalDateTime actualDateTime = null;
        if (dateVal instanceof LocalDateTime) {
            actualDateTime = (LocalDateTime) dateVal;
        } else if (dateVal instanceof Timestamp) {
            actualDateTime = ((Timestamp) dateVal).toLocalDateTime();
        } else if (dateVal instanceof String) {
            actualDateTime = LocalDateTime.parse((String) dateVal);
        }

        assertEquals(LocalDateTime.of(2023, 10, 1, 0, 0), actualDateTime);

        // 验证数据库存储
        Map<String, Object> dbRecord = jdbc.queryForMap(
                "SELECT * FROM ext_data_reserved WHERE entity_id = :entity_id and tenant_id=:tenant_id " +
                        " and app_code=:app_code and biz_identity_code=:biz_identity_code and entity_type=:entity_type" +
                        " and deleted=false",
                Map.of(
                        "tenant_id", TenantContext.getTenantId(),
                        "app_code", "extTest",
                        "biz_identity_code", "bone",
                        "entity_type", entityType,
                        "entity_id", id
                )
        );

        assertNotNull(dbRecord.get("ext_str_01"));
        assertNotNull(dbRecord.get("ext_int_01"));
        assertNotNull(dbRecord.get("ext_boolean_01"));
        assertNotNull(dbRecord.get("ext_date_01"));

        cleanUpTestData(id, fieldDefinitions);
    }

    @Test
    void testQueryByExtensionField_ShouldFilterCorrectly() {
        TenantContext.setTenantId(100L);
        String entityType = "Permission";

        // 预注册字段元数据
        List<FieldMetadata> fieldDefinitions = Arrays.asList(
                createFieldMetadata(entityType, "department", DataType.STRING)
        );
        metadataService.allocateAndPersistFields(fieldDefinitions);

        // BizIdentityContext.setBizIdentityCode("bone"); // 注释掉不存在的方法调用
        // 准备测试数据
        DataPermission perm1 = createBasePermission("QUERY_TEST_1");
        perm1.putExtraProperty("department", "Engineering");
        Long id1 = permissionRepository.insert(perm1);

        DataPermission perm2 = createBasePermission("QUERY_TEST_2");
        perm2.putExtraProperty("department", "Product");
        Long id2 = permissionRepository.insert(perm2);

        // 执行扩展字段查询
        Criteria<Permission> criteria = Criteria.<Permission>create()
                .eq(Permission::getPermName, "QUERY_TEST_1")
                .eqExtra("department", "Engineering");
        List<Permission> results = permissionRepository.findByCriteria(criteria);
        Long count = permissionRepository.countByCriteria(criteria);

        assertEquals(count, results.size());
        assertEquals(1, results.size());
        assertEquals("QUERY_TEST_1", results.get(0).getPermName());

        cleanUpTestData(id1, fieldDefinitions);
        cleanUpTestData(id2, fieldDefinitions);
    }

    @Test
    void testConcurrentExtensionFieldUpdates_ShouldMaintainConsistency() throws InterruptedException {
        TenantContext.setTenantId(100L);
        String entityType = "Permission";

        // 预注册字段元数据
        List<FieldMetadata> fieldDefinitions = Arrays.asList(
                createFieldMetadata(entityType, "counter", DataType.INTEGER)
        );
        metadataService.allocateAndPersistFields(fieldDefinitions);

        DataPermission perm = createBasePermission("CONCURRENT_TEST");
        Long id = permissionRepository.insert(perm);

        Runnable updateTask = () -> {
            for (int i = 0; i < 20; i++) {
                Permission p = permissionRepository.findById(id);
                p.putExtraProperty("counter", i);
                permissionRepository.update(p);
            }
        };

        Thread t1 = new Thread(updateTask);
        Thread t2 = new Thread(updateTask);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        Permission finalPerm = permissionRepository.findById(id);
        assertTrue(finalPerm.getExtraProperty("counter", Long.class).orElse(0L) >= 19);

        cleanUpTestData(id, fieldDefinitions);
    }

    @Test
    void testUndefinedFieldQuery_ShouldThrowException() {
        Criteria<Permission> criteria = Criteria.<Permission>create()
                .eq("undefinedField", "invalid");

        assertThrows(UndefinedFieldException.class, () -> {
            permissionRepository.findByCriteria(criteria);
        });
    }

    @Test
    void testNullValueHandling_ShouldPersistCorrectly() {
        TenantContext.setTenantId(100L);
        String entityType = "Permission";

        // 预注册字段元数据
        List<FieldMetadata> fieldDefinitions = List.of(
                createFieldMetadata(entityType, "nullableField", DataType.STRING)
        );
        metadataService.allocateAndPersistFields(fieldDefinitions);

        DataPermission perm = createBasePermission("NULL_HANDLING_TEST");
        perm.putExtraProperty("nullableField", null);

        Long id = permissionRepository.insert(perm);
        Permission retrieved = permissionRepository.findById(id);

        assertTrue(retrieved.getExtraProperties().containsKey("nullableField"));
        assertNull(retrieved.getExtraProperty("nullableField"));

        cleanUpTestData(id, fieldDefinitions);
    }
}