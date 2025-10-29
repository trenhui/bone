package com.bone.metadata.sdk.test.testcase;

import com.bone.core.enums.Operator;
import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.metadata.sdk.domain.enums.SortDirection;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.domain.dto.UserRoleDTO;
import com.bone.metadata.sdk.test.domain.dto.UserWithRoleDTO;
import com.bone.metadata.sdk.test.domain.query.UserPageQuery;
import com.bone.metadata.sdk.test.domain.query.UserQuery;
import com.bone.metadata.sdk.test.domain.request.UserSearchRequest;
import com.bone.metadata.sdk.test.repository.proxy.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
@Transactional
@Rollback
public class UserSqlRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Clear database and insert test data
        jdbcTemplate.update("DELETE FROM roles");
        jdbcTemplate.update("DELETE FROM users");

        // Insert test roles
        jdbcTemplate.update("INSERT INTO roles (id, role_name, description) VALUES (?, ?, ?)",
                1L, "Admin", "Administrator role");
        jdbcTemplate.update("INSERT INTO roles (id, role_name, description) VALUES (?, ?, ?)",
                2L, "User", "Standard user role");
        jdbcTemplate.update("INSERT INTO roles (id, role_name, description) VALUES (?, ?, ?)",
                3L, "Guest", "Guest user role");

        // Insert test users - 确保所有用户都有有效的 role_id
        jdbcTemplate.update(
                "INSERT INTO users (id, name, role_id, create_time, create_by, update_time, update_by, deleted) VALUES (?, ?, ?, NOW(), ?, NULL, NULL, 0)",
                1L, "Alice", 1L, 1001L);
        jdbcTemplate.update(
                "INSERT INTO users (id, name, role_id, create_time, create_by, update_time, update_by, deleted) VALUES (?, ?, ?, NOW(), ?, NULL, NULL, 0)",
                2L, "Bob", 2L, 1002L);
        jdbcTemplate.update(
                "INSERT INTO users (id, name, role_id, create_time, create_by, update_time, update_by, deleted) VALUES (?, ?, ?, NOW(), ?, NULL, NULL, 0)",
                3L, "Charlie", 2L, 1003L);
        jdbcTemplate.update(
                "INSERT INTO users (id, name, role_id, create_time, create_by, update_time, update_by, deleted) VALUES (?, ?, ?, NOW(), ?, NULL, NULL, 1)",
                4L, "DeletedUser", 1L, 1004L); // Soft-deleted user

        // 验证数据完整性
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role_id IS NULL", Integer.class);
        assertEquals(0, count, "All users should have a valid role_id");
    }

    // 基础 CRUD 测试
    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Find by existing ID returns user")
        void testFindById_ExistingId_ReturnsUser() {
            User user = userRepository.findById(1L);
            assertNotNull(user, "User should not be null");
            assertEquals("Alice", user.getName(), "User name should be Alice");
            assertEquals(1L, user.getRoleId(), "Role ID should be 1");
        }

        @Test
        @DisplayName("Find by non-existing ID returns null")
        void testFindById_NonExistingId_ReturnsNull() {
            User user = userRepository.findById(999L);
            assertNull(user, "Non-existing user should return null");
        }

        @Test
        @DisplayName("Find by IDs returns only active users")
        void testFindByIds_ValidIds_ReturnsUsers() {
            List<User> users = userRepository.findByIds(Arrays.asList(1L, 2L, 4L));
            assertEquals(2, users.size(), "Should find two active users");
            Set<Long> ids = users.stream().map(User::getId).collect(Collectors.toSet());
            assertTrue(ids.contains(1L) && ids.contains(2L), "Should contain IDs 1 and 2");
            assertFalse(ids.contains(4L), "Should not contain deleted user ID 4");
        }

        @Test
        @DisplayName("Find by empty IDs list returns empty list")
        void testFindByIds_EmptyList_ReturnsEmptyList() {
            List<User> users = userRepository.findByIds(Collections.emptyList());
            assertTrue(users.isEmpty(), "Empty ID list should return empty user list");
        }

        @Test
        @DisplayName("Find by ID including deleted returns all users")
        void testFindByIdIncludingDeleted_ReturnsAllUsers() {
            User user = userRepository.findByIdIncludingDeleted(4L);
            assertNotNull(user, "Should find deleted user when including deleted");
            assertEquals("DeletedUser", user.getName(), "User name should be DeletedUser");
        }

        @Test
        @DisplayName("Insert user returns generated ID")
        void testInsert_ValidUser_ReturnsGeneratedId() {
            User newUser = createUser("NewUser", 2L, 1005L);
            Long id = userRepository.insert(newUser);
            assertNotNull(id, "Insert should return generated ID");
            assertTrue(id > 0, "Generated ID should be positive");

            User retrievedUser = userRepository.findById(id);
            assertNotNull(retrievedUser, "Inserted user should be retrievable");
            assertEquals("NewUser", retrievedUser.getName(), "User name should match");
        }

        @Test
        @DisplayName("Batch insert multiple users succeeds")
        void testBatchInsert_MultipleUsers_InsertsAll() {
            List<User> users = Arrays.asList(
                    createUser("User1", 1L, 1001L),
                    createUser("User2", 2L, 1002L)
            );

            userRepository.batchInsert(users);

            List<User> fetchedUsers = userRepository.findByName("User");
            assertEquals(2, fetchedUsers.size(), "Should find two inserted users");
        }

        @Test
        @DisplayName("Update user with all fields")
        void testUpdate_UserWithPartialFields_UpdatesAllFields() {
            // First get the original user
            User originalUser = userRepository.findById(1L);
            assertNotNull(originalUser);

            // Create an update with only name changed
            User update = new User();
            update.setId(1L);
            update.setName("Alice Updated");

            boolean result = userRepository.update(update);
            assertTrue(result, "Update should succeed");

            // Verify only name was updated
            User updatedUser = userRepository.findById(1L);
            assertEquals("Alice Updated", updatedUser.getName(), "Name should be updated");
        }

        @Test
        @DisplayName("Delete by ID soft deletes user")
        void testDeleteById_ExistingUser_SoftDeletesUser() {
            boolean result = userRepository.deleteById(1L);
            assertTrue(result, "Delete should succeed");

            User user = userRepository.findById(1L);
            assertNull(user, "Deleted user should not be found in normal queries");

            User deletedUser = userRepository.findByIdIncludingDeleted(1L);
            assertNotNull(deletedUser, "Deleted user should be found when including deleted");
            assertEquals(true, deletedUser.getDeleted(), "Deleted flag should be set to 1");
        }

        @Test
        @DisplayName("Delete by IDs soft deletes multiple users")
        void testDeleteByIds_MultipleUsers_SoftDeletesAll() {
            userRepository.deleteByIds(Arrays.asList(1L, 2L));

            List<User> users = userRepository.findByIds(Arrays.asList(1L, 2L));
            assertTrue(users.isEmpty(), "No users should be found after deletion");

            List<User> deletedUsers = userRepository.findByIdsIncludingDeleted(Arrays.asList(1L, 2L));
            assertEquals(2, deletedUsers.size(), "Both users should be found when including deleted");
        }

        @Test
        @DisplayName("Save new user performs insert")
        void testSave_NewUser_PerformsInsert() {
            User newUser = createUser("NewUser", 2L, 1005L);
            newUser.setId(null); // Ensure no ID set

            Long id = userRepository.save(newUser);
            assertNotNull(id, "Save should return generated ID");

            User savedUser = userRepository.findById(id);
            assertNotNull(savedUser, "Saved user should be retrievable");
            assertEquals("NewUser", savedUser.getName(), "User name should match");
        }

        @Test
        @DisplayName("Save existing user performs update")
        void testSave_ExistingUser_PerformsUpdate() {
            User existingUser = userRepository.findById(1L);
            existingUser.setName("Alice Updated");

            Long id = userRepository.save(existingUser);
            assertEquals(1L, id, "Save should return same ID for update");

            User updatedUser = userRepository.findById(1L);
            assertEquals("Alice Updated", updatedUser.getName(), "User name should be updated");
        }

        @Test
        @DisplayName("Batch save mixed new and existing users")
        void testBatchSave_MixedUsers_InsertsAndUpdates() {
            // Get existing user to update
            User existingUser = userRepository.findById(1L);
            existingUser.setName("Alice Updated");

            // Create new user
            User newUser = createUser("NewUser", 2L, 1005L);
            newUser.setId(null);

            userRepository.batchSave(Arrays.asList(existingUser, newUser));

            // Verify update
            User updatedUser = userRepository.findById(1L);
            assertEquals("Alice Updated", updatedUser.getName(), "Existing user should be updated");

            // Verify insert (find by name since we don't know the generated ID)
            List<User> newUsers = userRepository.findByName("NewUser");
            assertFalse(newUsers.isEmpty(), "New user should be inserted");
        }

        private User createUser(String name, Long roleId, Long createBy) {
            User user = new User();
            user.setName(name);
            user.setRoleId(roleId);
            user.setCreateBy(createBy);
            user.setDeleted(false);
            return user;
        }
    }

    // Criteria-based 查询测试
    @Nested
    @DisplayName("Criteria-based Operations")
    class CriteriaTests {

        @Test
        @DisplayName("Find by criteria returns matching users")
        void testFindByCriteria_WithConditions_ReturnsMatchingUsers() {
            // Create criteria to find users with role ID 2
            Criteria<User> criteria = Criteria.<User>create().eq(User::getRoleId,2L);
            List<User> users = userRepository.findByCriteria(criteria);
            assertEquals(2, users.size(), "Should find two users with role ID 2");
            assertTrue(users.stream().allMatch(u -> u.getRoleId() == 2L),
                    "All users should have role ID 2");
        }

        @Test
        @DisplayName("Find one by criteria returns single result")
        void testFindOneByCriteria_WithUniqueCondition_ReturnsSingleUser() {
            // Create criteria to find user with specific ID
            Criteria<User> criteria = Criteria.<User>builder()
                    .eq(User::getId, 1L);

            User user = userRepository.findOneByCriteria(criteria);
            assertNotNull(user, "Should find user with ID 1");
            assertEquals("Alice", user.getName(), "User name should be Alice");
        }

        @Test
        @DisplayName("Find one by criteria with multiple results throws exception")
        void testFindOneByCriteria_WithMultipleResults_ThrowsException() {
            // Create criteria that will match multiple users
            Criteria<User> criteria = Criteria.<User>builder().eq(User::getRoleId,2L);

            assertThrows(MultipleResultsException.class, () -> {
                userRepository.findOneByCriteria(criteria);
            }, "Should throw exception when multiple results found");
        }

        @Test
        @DisplayName("Page by criteria returns paged results")
        void testPageByCriteria_WithPaging_ReturnsPagedResults() {
            // Create criteria with paging
            Criteria<User> criteria = Criteria.<User>builder()
                    .page(1,2)
                    .addSort(User::getId, SortDirection.ASC);

            PageResult<User> page = userRepository.pageByCriteria(criteria);
            assertNotNull(page, "Page result should not be null");
            assertEquals(3, page.getTotalCount(), "Should have 3 total users");
            assertEquals(2, page.getData().size(), "Should return 2 users per page");
        }

        @Test
        @DisplayName("Count by criteria returns correct count")
        void testCountByCriteria_WithConditions_ReturnsCorrectCount() {
            // Create criteria to count users with role ID 2
            Criteria<User> criteria = Criteria.<User>builder().eq(User::getRoleId,2L);

            Long count = userRepository.countByCriteria(criteria);
            assertEquals(2L, count, "Should count 2 users with role ID 2");
        }
    }

    // Named Statement 测试
    @Nested
    @DisplayName("Named Statement Operations")
    class NamedStatementTests {

        @Test
        @DisplayName("Execute named statement with parameters returns result")
        void testExecuteNamedStatement_WithParameters_ReturnsResult() {
            Map<String, Object> params = new HashMap<>();
            params.put("name", "Alice");

            // Assuming there's a named statement "findUserByName"
            List<User> result = userRepository.executeNamedStatement("findUserByName", params);
            assertNotNull(result, "Should find user with name Alice");
            assertEquals(1L, result.get(0).getId(), "User ID should be 1");
        }

        @Test
        @DisplayName("Execute named statement with row mapper returns converted results")
        void testExecuteNamedStatement_WithRowMapper_ReturnsConvertedResults() {
            Map<String, Object> params = new HashMap<>();
            params.put("roleId", 2L);

            RowMapper<User> rowMapper = new RowMapper<User>() {
                @Override
                public User mapRow(ResultSet rs, int rowNum) throws SQLException {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setName(rs.getString("name"));
                    user.setRoleId(rs.getLong("roleId"));
                    return user;
                }
            };

            // Assuming there's a named statement "findUsersByRole"
            List<User> users = userRepository.executeNamedStatement("findUsersByRole", params, rowMapper);
            assertEquals(2, users.size(), "Should find 2 users with role ID 2");
        }

        @Test
        @DisplayName("Execute named statement for map returns map results")
        void testExecuteNamedStatementForMap_WithParameters_ReturnsMapResults() {
            Map<String, Object> params = new HashMap<>();
            params.put("id", 1L);

            // Assuming there's a named statement "findUserByIdForMap"
            List<Map<String, Object>> results = userRepository.executeNamedStatementForMap("findUserByIdForMap", params);
            assertFalse(results.isEmpty(), "Should find user with ID 1");
            assertEquals("Alice", results.get(0).get("name"), "User name should be Alice");
        }

        @Test
        @DisplayName("Execute paged named statement returns paged results")
        void testExecutePagedNamedStatement_WithParameters_ReturnsPagedResults() {
            Map<String, Object> params = new HashMap<>();
            params.put("deleted", 0);

            RowMapper<User> rowMapper = new RowMapper<User>() {
                @Override
                public User mapRow(ResultSet rs, int rowNum) throws SQLException {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setName(rs.getString("name"));
                    return user;
                }
            };

            // Assuming there's a named statement "findActiveUsersPaged"
            PageResult<User> page = userRepository.executePagedNamedStatement(
                    "findActiveUsersPaged", params, rowMapper, 1, 2);

            assertNotNull(page, "Page result should not be null");
            assertEquals(3, page.getTotalCount(), "Should have 3 total active users");
            assertEquals(2, page.getData().size(), "Should return 2 users per page");
        }

        @Test
        @DisplayName("Execute paged named statement with param bean returns paged results")
        void testExecutePagedNamedStatement_WithParamBean_ReturnsPagedResults() {
            UserSearchRequest request = new UserSearchRequest();
            request.setRoleId(2L);

            // Assuming there's a named statement "searchUsersPaged" that accepts UserSearchRequest
            PageResult<UserRoleDTO> page = userRepository.executePagedNamedStatement("searchUsersPaged", request);

            assertNotNull(page, "Page result should not be null");
            assertEquals(2, page.getTotalCount(), "Should have 2 total users with role ID 2");
        }
    }

    // 通用查询测试
    @Nested
    @DisplayName("Generic Query Operations")
    class GenericQueryTests {

        @Test
        @DisplayName("Query by condition with parameters returns results")
        void testQueryByCondition_WithParameters_ReturnsResults() {
            List<QueryParam> queryParams = Arrays.asList(
                    new QueryParam("name", "Ali", Operator.LIKE),
                    new QueryParam("role_id", 1L,Operator.EQ)
            );

            List<SortingField> sortingFields = Arrays.asList(
                    new SortingField("create_time", SortingField.ORDER_DESC)
            );

            PageResult<User> page = userRepository.queryByCondition(
                    queryParams, sortingFields, 1, 10, "user");

            assertNotNull(page, "Page result should not be null");
            assertEquals(1, page.getTotalCount(), "Should find 1 user matching criteria");
            assertEquals("Alice", page.getData().get(0).getName(), "User name should be Alice");
        }

        @Test
        @DisplayName("Query with query object returns results")
        void testQuery_WithQueryObject_ReturnsResults() {
            UserQuery query = new UserQuery();
            query.setUserName("Bob");
            query.setRoleId(2L);

            List<User> users = userRepository.query(query);
            assertFalse(users.isEmpty(), "Should find user with name Bob");
            assertEquals("Bob", users.get(0).getName(), "User name should be Bob");
        }

        @Test
        @DisplayName("Query page with page param returns paged results")
        void testQueryPage_WithPageParam_ReturnsPagedResults() {
            UserPageQuery pageQuery = new UserPageQuery();
            pageQuery.setPageNo(1);
            pageQuery.setPageSize(2);
            pageQuery.setUserName("A");

            PageResult<User> page = userRepository.queryPage(pageQuery);
            assertNotNull(page, "Page result should not be null");
            assertTrue(page.getTotalCount() >= 1, "Should find at least 1 user with name containing A");
            assertEquals(2, page.getData().size(), "Should return up to 2 users per page");
        }
    }

    // 聚合查询测试
    @Nested
    @DisplayName("Aggregation Operations")
    class AggregationTests {

        @Test
        @DisplayName("Aggregate with simple aggregation returns result")
        void testAggregate_SimpleAggregation_ReturnsResult() {
            List<String> aggregations = Arrays.asList("COUNT(*)", "MAX(id)");

            // 修改这里：查询未删除用户 (deleted = false)
            Criteria<User> criteria = Criteria.<User>builder().eq(User::getDeleted, false);

            Map<String, Object> result = userRepository.aggregate(aggregations, criteria);
            assertNotNull(result, "Aggregation result should not be null");
            assertEquals(3L, result.get("COUNT(*)"), "Should count 3 active users"); // 改为3
            assertTrue((Long) result.get("MAX(id)") >= 3L, "Max ID should be at least 3");
        }

        @Test
        @DisplayName("Aggregate with group by returns grouped results")
        void testAggregate_WithGroupBy_ReturnsGroupedResults() {
            List<String> aggregations = Arrays.asList("COUNT(*)");
            List<String> groupBy = Arrays.asList("role_id");

            Criteria<User> criteria = Criteria.<User>builder().eq(User::getDeleted, false);

            List<Map<String, Object>> results = userRepository.aggregate(aggregations, criteria, groupBy);
            assertFalse(results.isEmpty(), "Should return grouped results");

            // 应该有两个组：role_id 1 和 2
            assertEquals(2, results.size(), "Should have 2 groups");

            // 查找 role_id 1 的计数
            Optional<Map<String, Object>> role1Result = results.stream()
                    .filter(r -> {
                        Object roleId = r.get("role_id");
                        return roleId != null && 1L == ((Number) roleId).longValue();
                    })
                    .findFirst();

            assertTrue(role1Result.isPresent(), "Should have result for role_id 1");
            assertEquals(1L, role1Result.get().get("COUNT(*)"), "Should have 1 user with role_id 1");

            // 添加对 role_id 2 的验证
            Optional<Map<String, Object>> role2Result = results.stream()
                    .filter(r -> {
                        Object roleId = r.get("role_id");
                        return roleId != null && 2L == ((Number) roleId).longValue();
                    })
                    .findFirst();

            assertTrue(role2Result.isPresent(), "Should have result for role_id 2");
            assertEquals(2L, role2Result.get().get("COUNT(*)"), "Should have 2 users with role_id 2");
        }

        @Test
        @DisplayName("Aggregate with group by and having returns filtered results")
        void testAggregate_WithGroupByAndHaving_ReturnsFilteredResults() {
            List<String> aggregations = Arrays.asList("COUNT(*)");
            List<String> groupBy = Arrays.asList("role_id");
            List<String> having = Arrays.asList("COUNT(*) > 1");

            // 修改这里：查询未删除用户 (deleted = false)
            Criteria<User> criteria = Criteria.<User>builder().eq(User::getDeleted, false);

            List<Map<String, Object>> results = userRepository.aggregate(aggregations, criteria, groupBy, having);
            assertFalse(results.isEmpty(), "Should return filtered results");

            // 应该只有一个组：role_id 2 (count > 1)
            assertEquals(1, results.size(), "Should have 1 group with count > 1");
            assertEquals(2L, results.get(0).get("role_id"), "Should be role_id 2");
            assertEquals(2L, results.get(0).get("COUNT(*)"), "Should have 2 users with role_id 2");
        }

        @Test
        @DisplayName("Aggregate with pagination returns paged results")
        void testAggregateWithPagination_WithGroupBy_ReturnsPagedResults() {
            List<String> aggregations = Arrays.asList("COUNT(*)");
            List<String> groupBy = Arrays.asList("role_id");

            // 修改这里：查询未删除用户 (deleted = false)
            Criteria<User> criteria = Criteria.<User>builder().eq(User::getDeleted, false);

            PageResult<Map<String, Object>> page = userRepository.aggregateWithPagination(
                    aggregations, criteria, groupBy, null,1, 10);

            assertNotNull(page, "Page result should not be null");
            assertEquals(2, page.getTotalCount(), "Should have 2 total groups"); // 改为2
            assertEquals(2, page.getData().size(), "Should return 2 groups per page");
        }
    }

    // 特定于 UserRepository 的测试
    @Nested
    @DisplayName("UserRepository Specific Operations")
    class UserRepositorySpecificTests {

        @Test
        @DisplayName("Find by name returns matching users")
        void testFindByName_MatchingName_ReturnsUsers() {
            List<User> users = userRepository.findByName("Alice");
            assertFalse(users.isEmpty(), "Users list should not be empty");
            assertEquals(1, users.size(), "Should find one user");
            assertEquals("Alice", users.get(0).getName(), "User name should be Alice");
        }

        @Test
        @DisplayName("Find by role ID returns users with that role")
        void testFindByRoleId_ExistingRoleId_ReturnsUsers() {
            List<User> users = userRepository.findByRoleId(2L);
            assertEquals(2, users.size(), "Should find two users with role ID 2");
            assertTrue(users.stream().allMatch(u -> u.getRoleId() == 2L),
                    "All users should have role ID 2");
        }

        @Test
        @DisplayName("Find users with role returns joined data")
        void testFindUsersWithRole_NameAndRoleId_ReturnsUsers() {
            List<UserWithRoleDTO> users = userRepository.findUsersWithRole("Alice", 1L);
            assertFalse(users.isEmpty(), "Users list should not be empty");
            assertEquals(1, users.size(), "Should find one user");
            assertEquals("Alice", users.get(0).getName(), "User name should be Alice");
            assertEquals("Admin", users.get(0).getRoleName(), "Role name should be Admin");
        }

        @Test
        @DisplayName("Search users with conditions returns paged result")
        void testSearchUsers_WithConditions_ReturnsPagedResult() {
            UserSearchRequest request = new UserSearchRequest();
            request.setName("Bob");
            request.setRoleId(2L);

            List<UserWithRoleDTO> result = userRepository.searchUsers(request);
            assertNotNull(result, "PageResult should not be null");
            assertEquals(1, result.size(), "Should find one user");
            assertEquals("Bob", result.get(0).getName(), "User name should be Bob");
        }

        @Test
        @DisplayName("Update name for existing user succeeds")
        void testUpdateName_ValidInput_ReturnsUpdatedCount() {
            int updated = userRepository.updateName(1L, "Alice Updated", 1001L);
            assertEquals(1, updated, "Should update one record");

            User user = userRepository.findById(1L);
            assertEquals("Alice Updated", user.getName(), "User name should be updated");
        }

        @Test
        @DisplayName("Insert user and get last insert ID works")
        void testInsertUser_ValidInput_InsertsUser() {
            userRepository.insertUser("Charlie", 2L, 1003L);
            Long id = userRepository.getLastInsertId();
            assertNotNull(id, "Inserted ID should not be null");
            assertTrue(id > 0, "Inserted ID should be positive");

            User user = userRepository.findById(id);
            assertNotNull(user, "Inserted user should be found");
            assertEquals("Charlie", user.getName(), "User name should be Charlie");
        }
    }
}