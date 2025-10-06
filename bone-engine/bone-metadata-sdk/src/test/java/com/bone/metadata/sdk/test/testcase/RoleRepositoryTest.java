package com.bone.metadata.sdk.test.testcase;

import com.bone.core.enums.Operator;
import com.bone.core.model.PageResult;
import com.bone.core.model.QueryParam;
import com.bone.core.model.SortingField;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.Role;
import com.bone.metadata.sdk.test.repository.impl.RoleRepository;
import com.bone.metadata.sdk.test.utils.TestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
public class RoleRepositoryTest  {

    private final NamedParameterJdbcOperations jdbc;
    private final RoleRepository roleRepository;


    @Autowired
    public RoleRepositoryTest(NamedParameterJdbcOperations jdbc, RoleRepository roleRepository) {
        this.jdbc = jdbc;
        this.roleRepository = roleRepository;
    }

    @BeforeEach
    void setUp() {
        // Clear and set up test data before each test
        jdbc.getJdbcOperations().execute("DELETE FROM roles");
        TestDataHelper.setUpRoleTestData(jdbc);
    }

    // 1. Test findById
    @Test
    void testFindById_ShouldReturnRoleWhenFound() {
        Role role = roleRepository.findById(1L);
        assertNotNull(role, "Role should be found with ID 1");
        assertEquals("ADMIN", role.getRoleName(), "Role name should be ADMIN");
    }

    @Test
    void testFindById_ShouldReturnNullWhenNotFound() {
        Role role = roleRepository.findById(999L);
        assertNull(role, "Role should not be found with ID 999");
    }

    // 2. Test findByIds
    @Test
    void testFindByIds_ShouldReturnRolesWhenIdsExist() {
        List<Long> roleIds = Arrays.asList(1L, 2L, 3L);
        List<Role> roles = roleRepository.findByIds(roleIds);
        assertEquals(3, roles.size(), "Should return 3 roles");
        roles.forEach(role -> assertTrue(roleIds.contains(role.getId()), "Role ID should be in the requested list"));
    }

    @Test
    void testFindByIds_ShouldReturnEmptyListWhenNoIdsExist() {
        List<Long> nonExistentIds = Arrays.asList(999L, 1000L);
        List<Role> roles = roleRepository.findByIds(nonExistentIds);
        assertTrue(roles.isEmpty(), "Should return an empty list when no roles exist with the given IDs");
    }

    // 3. Test findByIdIgnoreDeleted (assuming no soft delete for Role)
    @Test
    void testFindByIdIgnoreDeleted_ShouldReturnRoleWhenFound() {
        Role role = roleRepository.findByIdIncludingDeleted(1L);
        assertNotNull(role, "Role should be found with ID 1");
    }

    @Test
    void testFindByIdIgnoreDeleted_ShouldReturnNullWhenNotFound() {
        Role role = roleRepository.findByIdIncludingDeleted(999L);
        assertNull(role, "Role should not be found with ID 999");
    }

    // 4. Test insert
    @Test
    void testInsert_ShouldInsertNewRole() {
        Role newRole = new Role(null, "NEW_ROLE", "New role description");
        Long generatedId = roleRepository.insert(newRole);
        assertNotNull(generatedId, "Generated ID should not be null");
        Role insertedRole = roleRepository.findById(generatedId);
        assertNotNull(insertedRole, "Inserted role should be found");
        assertEquals("NEW_ROLE", insertedRole.getRoleName(), "Role name should be NEW_ROLE");
    }

    // 5. Test batchInsert
    @Test
    void testBatchInsert_ShouldInsertMultipleRoles() {
        List<Role> newRoles = Arrays.asList(
                new Role(null, "BATCH_ROLE1", "Batch role 1"),
                new Role(null, "BATCH_ROLE2", "Batch role 2")
        );
        roleRepository.batchInsert(newRoles);
        List<Role> insertedRoles = roleRepository.findByCriteria(Criteria.<Role>create().in("role_name", Arrays.asList("BATCH_ROLE1", "BATCH_ROLE2")));
        assertEquals(2, insertedRoles.size(), "Should have inserted 2 roles");
    }

    // 6. Test update
    @Test
    void testUpdate_ShouldUpdateExistingRole() {
        Role roleToUpdate = roleRepository.findById(1L);
        roleToUpdate.setDescription("Updated description");
        boolean updated = roleRepository.update(roleToUpdate);
        assertTrue(updated, "Update should be successful");
        Role updatedRole = roleRepository.findById(1L);
        assertEquals("Updated description", updatedRole.getDescription(), "Description should be updated");
    }

    // 7. Test updateByCriteria
    @Test
    void testUpdateByCriteria_ShouldUpdateRolesBasedOnCriteria() {
        //Role updateEntity = new Role();
        Role updateEntity = new Role(1L, "ADMIN2", "Admin role with full permissions");

        updateEntity.setDescription("Batch updated description");
        Criteria<Role> criteria = Criteria.<Role>create().eq("role_name", "USER");
        int updatedRows = roleRepository.updateByCriteria(updateEntity, criteria);
        assertEquals(1, updatedRows, "Should update 1 role");
        Criteria<Role> criteria2 = Criteria.<Role>create().eq("role_name", "ADMIN2");
        Role updatedRole = roleRepository.findOneByCriteria(criteria2);
        assertEquals("Batch updated description", updatedRole.getDescription(), "Description should be updated");
    }

    // 8. Test save
    @Test
    void testSave_ShouldInsertNewRoleWhenIdIsNull() {
        Role newRole = new Role(null, "SAVE_NEW", "Save new role");
        Long generatedId = roleRepository.save(newRole);
        assertNotNull(generatedId, "Generated ID should not be null");
        Role savedRole = roleRepository.findById(generatedId);
        assertNotNull(savedRole, "Saved role should be found");
    }

    @Test
    void testSave_ShouldUpdateExistingRoleWhenIdIsNotNull() {
        Role roleToSave = roleRepository.findById(1L);
        roleToSave.setDescription("Saved description");
        Long savedId = roleRepository.save(roleToSave);
        assertEquals(1L, savedId, "Saved ID should be 1");
        Role savedRole = roleRepository.findById(1L);
        assertEquals("Saved description", savedRole.getDescription(), "Description should be updated");
    }

    // 9. Test batchSave
    @Test
    void testBatchSave_ShouldInsertAndUpdateRoles() {
        Role existingRole = roleRepository.findById(1L);
        existingRole.setDescription("Updated via batch save");
        Role newRole = new Role(null, "BATCH_SAVE_NEW", "New role via batch save");
        List<Role> rolesToSave = Arrays.asList(existingRole, newRole);
        roleRepository.batchSave(rolesToSave);
        Role updatedRole = roleRepository.findById(1L);
        assertEquals("Updated via batch save", updatedRole.getDescription(), "Existing role should be updated");
        List<Role> newRoles = roleRepository.findByCriteria(Criteria.<Role>create().eq("role_name", "BATCH_SAVE_NEW"));
        assertFalse(newRoles.isEmpty(), "New role should be inserted");
    }

    // 10. Test deleteById
    @Test
    void testDeleteById_ShouldDeleteRole() {
        boolean deleted = roleRepository.deleteById(1L);
        assertTrue(deleted, "Delete should be successful");
        Role deletedRole = roleRepository.findById(1L);
        assertNull(deletedRole, "Role should be deleted");
    }

    // 11. Test deleteByIds
    @Test
    void testDeleteByIds_ShouldDeleteMultipleRoles() {
        List<Long> roleIdsToDelete = Arrays.asList(1L, 2L);
        roleRepository.deleteByIds(roleIdsToDelete);
        List<Role> deletedRoles = roleRepository.findByIds(roleIdsToDelete);
        assertTrue(deletedRoles.isEmpty(), "Roles should be deleted");
    }

    // 12. Test findByCriteria
    @Test
    void testFindByCriteria_ShouldReturnRolesBasedOnCriteria() {
        Criteria<Role> criteria = Criteria.<Role>create().eq("role_name", "ADMIN");
        List<Role> roles = roleRepository.findByCriteria(criteria);
        assertFalse(roles.isEmpty(), "Should return roles with role_name ADMIN");
        roles.forEach(role -> assertEquals("ADMIN", role.getRoleName(), "Role name should be ADMIN"));
    }

    // 13. Test findOneByCriteria
    @Test
    void testFindOneByCriteria_ShouldReturnSingleRole() {
        Criteria<Role> criteria = Criteria.<Role>create().eq("id", 1L);
        Role role = roleRepository.findOneByCriteria(criteria);
        assertNotNull(role, "Role should be found with id 1");
    }

    @Test
    void testFindOneByCriteria_ShouldThrowExceptionWhenMultipleResults() {
        Criteria<Role> criteria = Criteria.<Role>create().like("description", "%role%");
        assertThrows(MultipleResultsException.class, () -> roleRepository.findOneByCriteria(criteria),
                "Should throw MultipleResultsException when more than one model is found");
    }

    // 14. Test pageByCriteria
    @Test
    void testPageByCriteria_ShouldReturnPagedResults() {
        Criteria<Role> criteria = Criteria.<Role>create();
        criteria.setPageNo(1);
        criteria.setPageSize(2);
        PageResult<Role> pageResult = roleRepository.pageByCriteria(criteria);
        assertNotNull(pageResult, "Page model should not be null");
        assertTrue(pageResult.getRecords().size() <= 2, "Page should contain no more than 2 roles");
    }

    // 15. Test countByCriteria
    @Test
    void testCountByCriteria_ShouldReturnCorrectCount() {
        Criteria<Role> criteria = Criteria.<Role>create().eq("role_name", "ADMIN");
        Long count = roleRepository.countByCriteria(criteria);
        assertEquals(1L, count, "Count should be 1 for role_name ADMIN");
    }

    // 16. Test queryByCondition
    @Test
    void testQueryByCondition_ShouldReturnPagedResultsWithConditions() {
        List<QueryParam> queryParams = Collections.singletonList(new QueryParam("roleName",  "ADM",Operator.LIKE));
        List<SortingField> sortingFields = Collections.singletonList(new SortingField("id", "ASC"));
        PageResult<Role> pageResult = roleRepository.queryByCondition(queryParams, sortingFields, 1, 10, null);
        assertNotNull(pageResult, "Page model should not be null");
        assertFalse(pageResult.getRecords().isEmpty(), "Should return roles matching the condition");
    }
}