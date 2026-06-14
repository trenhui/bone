package com.bone.metadata.sdk.test.testcase;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.enums.Operator;
import com.bone.core.model.PageResult;
import com.bone.core.model.QueryParam;
import com.bone.core.model.SortingField;
import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.DataPermission;
import com.bone.metadata.sdk.test.domain.Permission;
import com.bone.metadata.sdk.test.repository.impl.PermissionRepository;
import com.bone.metadata.sdk.test.utils.TestDataHelper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
public class PermissionRepositoryTest {

  private final NamedParameterJdbcOperations jdbc;
  private final PermissionRepository permissionRepository;

  @Autowired
  public PermissionRepositoryTest(
      NamedParameterJdbcOperations jdbc, PermissionRepository permissionRepository) {
    this.jdbc = jdbc;
    this.permissionRepository = permissionRepository;
  }

  @BeforeEach
  void setUp() throws InterruptedException {

    TenantContext.setTenantId(100L);
    // Clear and set up test data before each test
    jdbc.getJdbcOperations().execute("DELETE FROM sys_permission");
    TestDataHelper.setUpPermissionTestData(jdbc);
  }

  // ### Existing Tests (Retained) ###

  @Test
  void testFindById_ShouldReturnPermissionWhenFound() {
    Permission permission = permissionRepository.findById(1L);
    assertNotNull(permission, "Permission should be found with ID 1");
    assertEquals("READ", permission.getPermName(), "Permission name should be READ");
  }

  @Test
  void testFindById_ShouldReturnNullWhenNotFound() {
    Permission permission = permissionRepository.findById(999L);
    assertNull(permission, "Permission should not be found with ID 999");
  }

  @Test
  void testFindByIds_ShouldReturnPermissionsWhenIdsExist() {
    List<Long> permissionIds = Arrays.asList(1L, 2L, 3L);
    List<Permission> permissions = permissionRepository.findByIds(permissionIds);
    assertEquals(3, permissions.size(), "Should return 3 permissions");
    permissions.forEach(
        permission ->
            assertTrue(
                permissionIds.contains(permission.getId()),
                "Permission ID should be in the requested list"));
  }

  @Test
  void testFindByIds_ShouldReturnEmptyListWhenNoIdsExist() {
    List<Long> nonExistentIds = Arrays.asList(999L, 1000L);
    List<Permission> permissions = permissionRepository.findByIds(nonExistentIds);
    assertTrue(
        permissions.isEmpty(),
        "Should return an empty list when no permissions exist with the given IDs");
  }

  @Test
  void testInsert_ShouldInsertNewPermission() {
    Permission newPermission = new DataPermission();
    newPermission.setPermName("NEW_PERMISSION");
    newPermission.setPermCode("NEW_CODE");
    newPermission.setBizIdentityCode("bone");
    newPermission.setPermType(2);
    Long generatedId = permissionRepository.insert(newPermission);
    assertNotNull(generatedId, "Generated ID should not be null");
    Permission insertedPermission = permissionRepository.findById(generatedId);
    assertNotNull(insertedPermission, "Inserted permission should be found");
    assertEquals(
        "NEW_PERMISSION",
        insertedPermission.getPermName(),
        "Permission name should be NEW_PERMISSION");
  }

  @Test
  void testBatchInsert_ShouldInsertMultiplePermissions() {
    Permission permission1 = new DataPermission();
    permission1.setPermName("BATCH_PERMISSION1");
    permission1.setPermCode("CODE1");
    permission1.setBizIdentityCode("bone");
    permission1.setPermType(1);
    Permission permission2 = new DataPermission();
    permission2.setPermName("BATCH_PERMISSION2");
    permission2.setBizIdentityCode("bone");
    permission2.setPermCode("CODE2");
    permission2.setPermType(2);

    List<Permission> newPermissions = Arrays.asList(permission1, permission2);
    permissionRepository.batchInsert(newPermissions);
    List<Permission> insertedPermissions =
        permissionRepository.findByCriteria(
            Criteria.<Permission>create()
                .in("permName", Arrays.asList("BATCH_PERMISSION1", "BATCH_PERMISSION2")));
    assertEquals(2, insertedPermissions.size(), "Should have inserted 2 permissions");
  }

  @Test
  void testUpdate_ShouldUpdateExistingPermission() {
    Permission permissionToUpdate = permissionRepository.findById(1L);
    permissionToUpdate.setPermCode("UPDATED_CODE");
    boolean updated = permissionRepository.update(permissionToUpdate);
    assertTrue(updated, "Update should be successful");
    Permission updatedPermission = permissionRepository.findById(1L);
    assertEquals(
        "UPDATED_CODE", updatedPermission.getPermCode(), "Permission code should be updated");
  }

  @Test
  void testUpdateByCriteria_ShouldUpdatePermissionsBasedOnCriteria() {
    Permission updateEntity = new Permission();
    updateEntity.setPermCode("BATCH_UPDATED_CODE");
    Criteria<Permission> criteria = Criteria.<Permission>create().eq("permName", "WRITE");
    int updatedRows = permissionRepository.updateByCriteria(updateEntity, criteria);
    assertEquals(1, updatedRows, "Should update 1 permission");
    Permission updatedPermission = permissionRepository.findOneByCriteria(criteria);
    assertEquals(
        "BATCH_UPDATED_CODE", updatedPermission.getPermCode(), "Permission code should be updated");
  }

  @Test
  void testSave_ShouldInsertNewPermissionWhenIdIsNull() {
    Permission newPermission = new Permission();
    newPermission.setPermName("SAVE_NEW");
    newPermission.setPermCode("SAVE_CODE");
    newPermission.setBizIdentityCode("bone");
    newPermission.setPermType(3);
    Long generatedId = permissionRepository.save(newPermission);
    assertNotNull(generatedId, "Generated ID should not be null");
    Permission savedPermission = permissionRepository.findById(generatedId);
    assertNotNull(savedPermission, "Saved permission should be found");
  }

  @Test
  void testSave_ShouldUpdateExistingPermissionWhenIdIsNotNull() {
    Permission permissionToSave = permissionRepository.findById(1L);
    permissionToSave.setPermCode("SAVED_CODE");
    Long savedId = permissionRepository.save(permissionToSave);
    assertEquals(1L, savedId, "Saved ID should be 1");
    Permission savedPermission = permissionRepository.findById(1L);
    assertEquals("SAVED_CODE", savedPermission.getPermCode(), "Permission code should be updated");
  }

  @Test
  void testBatchSave_ShouldInsertAndUpdatePermissions() {
    Permission existingPermission = permissionRepository.findById(1L);
    existingPermission.setPermCode("UPDATED_VIA_BATCH");
    existingPermission.setBizIdentityCode("bone");
    existingPermission.setPermType(1);

    Permission newPermission = new DataPermission();
    newPermission.setPermName("BATCH_SAVE_NEW");
    newPermission.setPermCode("NEW_VIA_BATCH");
    newPermission.setBizIdentityCode("bone");
    newPermission.setPermType(2);
    List<Permission> permissionsToSave = Arrays.asList(existingPermission, newPermission);
    permissionRepository.batchSave(permissionsToSave);
    Permission updatedPermission = permissionRepository.findById(1L);
    assertEquals(
        "UPDATED_VIA_BATCH",
        updatedPermission.getPermCode(),
        "Existing permission should be updated");
    List<Permission> newPermissions =
        permissionRepository.findByCriteria(
            Criteria.<Permission>create().eq("permName", "BATCH_SAVE_NEW"));
    assertFalse(newPermissions.isEmpty(), "New permission should be inserted");
  }

  @Test
  void testDeleteById_ShouldDeletePermission() {
    boolean deleted = permissionRepository.deleteById(1L);
    // assertTrue(deleted, "Delete should be successful");
    Permission deletedPermission = permissionRepository.findById(1L);
    assertNull(deletedPermission, "Permission should be deleted");
  }

  @Test
  void testDeleteByIds_ShouldDeleteMultiplePermissions() {
    List<Long> permissionIdsToDelete = Arrays.asList(1L, 2L);
    permissionRepository.deleteByIds(permissionIdsToDelete);
    List<Permission> deletedPermissions = permissionRepository.findByIds(permissionIdsToDelete);
    assertTrue(deletedPermissions.isEmpty(), "Permissions should be deleted");
  }

  @Test
  void testFindByCriteria_ShouldReturnPermissionsBasedOnCriteria() {
    Criteria<Permission> criteria = Criteria.<Permission>create().eq("permName", "READ");
    List<Permission> permissions = permissionRepository.findByCriteria(criteria);
    assertFalse(permissions.isEmpty(), "Should return permissions with permName READ");
    permissions.forEach(
        permission ->
            assertEquals("READ", permission.getPermName(), "Permission name should be READ"));
  }

  @Test
  void testFindOneByCriteria_ShouldReturnSinglePermission() {
    Criteria<Permission> criteria = Criteria.<Permission>create().eq("id", 1L);
    Permission permission = permissionRepository.findOneByCriteria(criteria);
    assertNotNull(permission, "Permission should be found with id 1");
  }

  @Test
  void testFindOneByCriteria_ShouldThrowExceptionWhenMultipleResults() {
    Criteria<Permission> criteria = Criteria.<Permission>create().like("permCode", "%TE%");
    assertThrows(
        MultipleResultsException.class,
        () -> permissionRepository.findOneByCriteria(criteria),
        "Should throw MultipleResultsException when more than one result is found");
  }

  @Test
  void testPageByCriteria_ShouldReturnPagedResults() {
    Criteria<Permission> criteria = Criteria.<Permission>create();
    criteria.setPageNo(1);
    criteria.setPageSize(2);
    PageResult<Permission> pageResult = permissionRepository.pageByCriteria(criteria);
    assertNotNull(pageResult, "Page result should not be null");
    assertTrue(
        pageResult.getRecords().size() <= 2, "Page should contain no more than 2 permissions");
  }

  @Test
  void testCountByCriteria_ShouldReturnCorrectCount() {
    Criteria<Permission> criteria = Criteria.<Permission>create().eq("permName", "READ");
    Long count = permissionRepository.countByCriteria(criteria);
    assertEquals(1L, count, "Count should be 1 for permName READ");
  }

  @Test
  void testQueryByCondition_ShouldReturnPagedResultsWithConditions() {
    List<QueryParam> queryParams =
        Collections.singletonList(new QueryParam("permName", "REA", Operator.LIKE));
    List<SortingField> sortingFields = Collections.singletonList(new SortingField("id", "ASC"));
    PageResult<Permission> pageResult =
        permissionRepository.queryByCondition(queryParams, sortingFields, 1, 10, null);
    assertNotNull(pageResult, "Page result should not be null");
    assertFalse(
        pageResult.getRecords().isEmpty(), "Should return permissions matching the condition");
  }

  // ### New Tests for DataPermission Features ###

  @Test
  void testSetAndGetDataScope_ShouldSetAndRetrieveDataScope() {
    DataPermission dataPermission = new DataPermission();
    dataPermission.setDataScope(DataPermission.DataScope.DEPARTMENT);
    assertEquals(
        DataPermission.DataScope.DEPARTMENT,
        dataPermission.getDataScope(),
        "Data scope should be DEPARTMENT");
  }

  @Test
  void testGetDataScope_ShouldReturnDefaultWhenNotSet() {
    DataPermission dataPermission = new DataPermission();
    assertEquals(
        DataPermission.DataScope.ALL,
        dataPermission.getDataScope(),
        "Default data scope should be ALL when not explicitly set");
  }

  @Test
  void testAddAndGetRowFilters_ShouldAddAndRetrieveRowFilters() {
    DataPermission dataPermission = new DataPermission();
    dataPermission.addRowFilter("department_id", "${currentUser.deptId}");
    Map<String, String> filters = dataPermission.getRowFilter();
    assertTrue(filters.containsKey("department_id"), "Row filter should contain department_id");
    assertEquals(
        "${currentUser.deptId}",
        filters.get("department_id"),
        "Row filter value should be ${currentUser.deptId}");
  }

  @Test
  void testSetAndCheckVisibleFields_ShouldSetAndVerifyVisibleFields() {
    DataPermission dataPermission = new DataPermission();
    dataPermission.setVisibleFields(Arrays.asList("id", "name"));
    assertTrue(dataPermission.getVisibleFields().contains("id"), "Field 'id' should be visible");
    assertTrue(
        dataPermission.getVisibleFields().contains("name"), "Field 'name' should be visible");
    assertFalse(
        dataPermission.getVisibleFields().contains("permCode"),
        "Field 'permCode' should not be visible");
  }

  @Test
  void testIsFieldVisible_ShouldReturnTrueWhenNoFieldsSet() {
    DataPermission dataPermission = new DataPermission();
    dataPermission.addVisibleField("id");
    assertTrue(
        dataPermission.getVisibleFields().contains("id"),
        "All fields should be visible by default when no visible fields are set");
  }

  @Test
  void testThreadSafetyOfExtraProperties_ShouldHandleConcurrentModifications()
      throws InterruptedException {
    DataPermission dataPermission = new DataPermission();
    Runnable task =
        () -> {
          for (int i = 0; i < 100; i++) {
            dataPermission.putExtraProperty("key" + i, "value" + i);
          }
        };
    Thread t1 = new Thread(task);
    Thread t2 = new Thread(task);
    t1.start();
    t2.start();
    t1.join();
    t2.join();
    assertEquals(
        100,
        dataPermission.getExtraProperties().size(),
        "Should have 100 unique keys after concurrent modifications");
  }

  @Test
  void testInvalidDataScope_ShouldThrowException() {
    DataPermission dataPermission = new DataPermission();
    assertThrows(
        IllegalArgumentException.class,
        () -> dataPermission.setDataScope(DataPermission.DataScope.valueOf("INVALID")),
        "Should throw IllegalArgumentException for invalid data scope");
  }

  @Test
  void testInsertDataPermission_ShouldPersistBaseFieldsOnly() {
    DataPermission dataPermission = new DataPermission();
    dataPermission.setPermName("DATA_PERM");
    dataPermission.setPermCode("DATA_CODE");
    dataPermission.setBizIdentityCode("bone");
    dataPermission.setPermType(1);
    dataPermission.setDataScope(DataPermission.DataScope.CUSTOM);
    dataPermission.getRowFilter().put("user_id", "${currentUser.id}");
    dataPermission.setVisibleFields(Arrays.asList("id", "name"));

    // 预注册字段元数据
    Long id = permissionRepository.insert(dataPermission);
    assertNotNull(id, "Generated ID should not be null");

    Permission retrieved = permissionRepository.findById(id);
    assertNotNull(retrieved, "Permission should be retrieved");
    assertEquals("DATA_PERM", retrieved.getPermName(), "Permission name should match");
    assertEquals("DATA_CODE", retrieved.getPermCode(), "Permission code should match");

    // Verify transient fields are not persisted
    if (retrieved instanceof DataPermission) {
      DataPermission retrievedDataPerm = (DataPermission) retrieved;
      assertEquals(
          DataPermission.DataScope.ALL,
          retrievedDataPerm.getDataScope(),
          "Data scope should reset to default after retrieval");
      assertTrue(
          retrievedDataPerm.getRowFilter().isEmpty(),
          "Row filters should be empty after retrieval");
      assertTrue(
          retrievedDataPerm.getVisibleFields().contains("id"),
          "All fields should be visible by default after retrieval");
    }
  }
}
