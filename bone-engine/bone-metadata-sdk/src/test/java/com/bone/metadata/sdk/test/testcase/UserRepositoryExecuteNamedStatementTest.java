package com.bone.metadata.sdk.test.testcase;

import com.bone.core.id.IdGenerator;
import com.bone.core.result.PageResult;
import com.bone.core.result.SortingField;
import com.bone.core.util.JsonUtil;
import com.bone.metadata.sdk.sql.executor.SmartRowMapper;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.domain.dto.UserRoleDTO;
import com.bone.metadata.sdk.test.domain.query.UserPageQuery;
import com.bone.metadata.sdk.test.domain.query.UserQuery;
import com.bone.metadata.sdk.test.repository.impl.UserRepositoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
public class UserRepositoryExecuteNamedStatementTest  {

    @Autowired
   private UserRepositoryImpl userRepository;

    @Autowired
    private NamedParameterJdbcOperations jdbc;

    // 1. 测试新增用户 (Create)
    @Test
    void testExecuteNamedStatement_CreateUser_ShouldInsertNewUser() {
        // Arrange
        Long id = IdGenerator.generateLongID();
        String username = "new user" + id;
        User newUser = new User(id, username, 2L, new Date(), id, new Date(), id, false);
        Map<String, Object> params = new HashMap<>();
        params.put("id", newUser.getId());
        params.put("name", newUser.getName());
        params.put("role_id", 2L); // Assuming role ID 2 exists

        // Act
        userRepository.executeNamedStatement("user_create", params);

        // Assert
        User result = userRepository.findById(newUser.getId());
        log.info("User: {}", result);
        assertNotNull(result, "User should be inserted into the database");
        assertEquals(newUser.getName(), result.getName(), "User name should be 'newuser'");
        assertEquals(2, result.getRoleId(), "User role should be 'USER'");
    }

    // 2. 测试更新用户 (Update)
    @Test
    void testExecuteNamedStatement_UpdateUser_ShouldUpdateExistingUser() {
        // Arrange
        Long userIdToUpdate = 1L; // Assuming user with ID 1 exists
        String updatedName = "admin1_updated";
        Long updatedRoleId = 2L; // Assuming role ID 2 exists

        Map<String, Object> params = new HashMap<>();
        params.put("id", userIdToUpdate);
        params.put("name", updatedName);
        params.put("role_id", updatedRoleId);

        // Act
        userRepository.executeNamedStatement("user_update", params);

        // Assert
        User result = userRepository.findById(userIdToUpdate);
        assertNotNull(result, "User should exist after update");
        assertEquals(updatedName, result.getName(), "User name should be updated");
        assertEquals(updatedRoleId, result.getRoleId(), "User role_id should be updated");
    }


    // 2. 测试查询用户及其角色 (Read with Role)
    @Test
    void testExecuteNamedStatement_SearchUsersWithRole_ShouldReturnUsersWithRoles() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("name", "%admin%"); // Use LIKE pattern for name
        params.put("roles", List.of("ADMIN", "USER")); // Search for users with specific roles

        // Act
        List<UserRoleDTO> result = userRepository.executeNamedStatement("user_search", params, (rs, rowNum) ->
                new UserRoleDTO(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("role")
                )
        );

        // Assert
        assertEquals(1, result.size(), "Should return 1 matching users with roles");
        result.forEach(user -> {
            assertTrue(user.getName().contains("admin"), "User name should contain 'admin'");
            assertTrue(List.of("ADMIN", "USER").contains(user.getRole()), "User role should be either 'ADMIN' or 'USER'");
        });
    }

    // 4. 测试删除用户 (Delete)
    @Test
    void testExecuteNamedStatement_DeleteUser_ShouldRemoveUser() {
        // Arrange
        Long userIdToDelete = 4L; // Assuming user with ID 4 exists

        Map<String, Object> params = new HashMap<>();
        params.put("id", userIdToDelete);

        // Act
        int i = userRepository.executeNamedStatement("user_delete", params);

        // Assert
        assertThrows(EmptyResultDataAccessException.class, () ->
                        jdbc.queryForObject("SELECT * FROM users WHERE id = :id",
                                Map.of("id", userIdToDelete), new SmartRowMapper<>(User.class)),
                "User should be deleted and no longer exist in the database"
        );
    }

    // 5. 测试通过用户名查询角色 (Read roles by username)
    @Test
    void testExecuteNamedStatement_SearchRolesByUsername_ShouldReturnRoles() {
        // Arrange
        String username = "sys_admin"; // Example username that exists in the database
        Map<String, Object> params = new HashMap<>();
        params.put("username", username);

        // Act
        List<UserRoleDTO> result = userRepository.executeNamedStatement("user_roles_by_username_search", params, (rs, rowNum) ->
                new UserRoleDTO(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("role")
                )
        );

        // Assert
        assertFalse(result.isEmpty(), "Should return at least one role for the username 'admin1'");
        result.forEach(user -> {
            assertEquals(username, user.getName(), "Username should be 'admin1'");
            assertNotNull(user.getRole(), "Role should not be null");
        });
    }

    @Test
    void testExecuteNamedStatement_Search_Page_ShouldReturnRoles() {
        // Arrange
        String username = "sys_admin"; // Example username that exists in the database
        Map<String, Object> params = new HashMap<>();
        params.put("username", username);


        // Act
        PageResult<UserRoleDTO> result = userRepository.executePagedNamedStatement("user_search_page", params, new SmartRowMapper<>(UserRoleDTO.class), 1, 10);

        // Assert
        assertTrue(result.getTotalCount() > 0, "Should return at least one role for the username 'admin1'");
//        result.forEach(user -> {
//            assertEquals(username, user.getName(), "Username should be 'admin1'");
//            assertNotNull(user.getRole(), "Role should not be null");
//        });
    }

    @Test
    void testexecutePagedNamedStatementt_QueryUerPermPage_ShouldReturnRoles() {
        // Arrange
        UserPageQuery userPageQuery = UserPageQuery.builder()
                .userName("ta")
                .roleName("ROOT_ADMIN")
                .build();

        userPageQuery.setPageNo(1);
        userPageQuery.setPageSize(10);

        // Act
        PageResult<UserRoleDTO> result = userRepository.queryUerPermPage( userPageQuery);
        log.info("result:"+JsonUtil.toJson(result));
        assertEquals(6, result.getTotalCount(), "Should return at least one role for the userName 'ROOT_ADMIN'");
    }


    @Test
    void testexecutePagedNamedStatementt_PageUerPermOrderBy_ShouldReturnRoles() {
        // Arrange
        UserPageQuery userPageQuery = UserPageQuery.builder()
                .userName("ta")
                .roleName("ROOT_ADMIN")
                .build();

        userPageQuery.setSortingFields(Arrays.asList(
                new SortingField("id", "desc"),
                new SortingField("userName", "asc")
        ));

        // Act
        PageResult<UserRoleDTO> result = userRepository.queryUerPermPageOrderBy( userPageQuery);
        log.info("result:"+JsonUtil.toJson(result));
        assertEquals(6, result.getTotalCount(), "Should return at least one role for the userName 'ROOT_ADMIN'");
    }

    @Test
    void testexecuteNamedStatementt_QueryUerPermOrderBy_ShouldReturnRoles() {
        // Arrange
        UserQuery userQuery = UserQuery.builder()
                .userName("ta")
                .roleName("ROOT_ADMIN")
                .build();

        userQuery.setSortingFields(Arrays.asList(
                new SortingField("id", "desc"),
                new SortingField("userName", "asc")
        ));

        // Act
        List<UserRoleDTO> result = userRepository.queryUerPermOrderBy( userQuery);
        log.info("result:"+JsonUtil.toJson(result));
        assertEquals(6, result.size(), "Should return at least one role for the userName 'ROOT_ADMIN'");
    }


}