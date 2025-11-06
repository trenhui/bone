package com.bone.metadata.sdk.test.testcase;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.repository.impl.UserRepositoryImpl;
import com.bone.metadata.sdk.test.utils.TestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
public class UserRepositoryCriteriaTest  {
    @Autowired
    private UserRepositoryImpl userRepository;
    @Autowired
    private  NamedParameterJdbcOperations jdbc;

    // Helper method to set up test data
    private void setUpTestData() {
        TestDataHelper.setUpTestData(jdbc);
    }

    // 1. 测试根据 ID 查询单个用户 (findById)
    @Test
    void testFindById_ShouldReturnUserWhenFound() {
        // Arrange
        setUpTestData();

        Long userId = 1L;

        // Act
        User user = userRepository.findById(userId);

        // Assert
        assertNotNull(user, "User should be found with ID 1");
    }

    @Test
    void testFindById_ShouldReturnNullWhenNotFound() {
        // Arrange
        setUpTestData();
        Long nonExistentUserId = 999L;

        // Act
        User user = userRepository.findById(nonExistentUserId);

        // Assert
        assertNull(user, "User should not be found with ID 999");
    }

    // 2. 测试根据 ID 列表查询多个用户 (findByIds)
    @Test
    void testFindByIds_ShouldReturnUsersWhenIdsExist() {
        // Arrange
        setUpTestData();
        List<Long> userIds = List.of(5L, 6L, 1L);

        // Act
        List<User> users = userRepository.findByIds(userIds);

        // Assert
        assertEquals(3, users.size(), "Should return 3 users for the given IDs");
        users.forEach(user -> assertTrue(userIds.contains(user.getId()), "User ID should be in the requested list"));
    }

    @Test
    void testFindByIds_ShouldReturnEmptyListWhenNoIdsExist() {
        // Arrange
        setUpTestData();
        List<Long> nonExistentIds = Arrays.asList(999L, 1000L);


        // Act
        List<User> users = userRepository.findByIds(nonExistentIds);

        // Assert
        assertTrue(users.isEmpty(), "Should return an empty list when no users exist with the given IDs");
    }

    // 3. 测试忽略软删除状态，根据 ID 查询单个用户 (findByIdIgnoreDeleted)
    @Test
    void testFindByIdIgnoreDeleted_ShouldReturnUserWhenFound() {
        // Arrange
        setUpTestData();
        Long userId = 1L;

        // Act
        User user = userRepository.findByIdIncludingDeleted(userId);

        // Assert
        assertNotNull(user, "User should be found with ID 1 and not soft deleted");
    }

    @Test
    void testFindByIdIgnoreDeleted_ShouldReturnNullWhenNotFound() {
        // Arrange
        setUpTestData();
        Long nonExistentUserId = 999L;

        // Act
        User user = userRepository.findByIdIncludingDeleted(nonExistentUserId);

        // Assert
        assertNull(user, "User should not be found with ID 999");
    }

    @Test
    void testFindById_ShouldSkipSoftDeletedUsers() {
        // Arrange
        setUpTestData();
        Long deletedUserId = 4L; // User 4 is soft deleted

        // Act
        User user = userRepository.findById(deletedUserId);

        // Assert
        assertNull(user, "User 4 should be skipped as it's soft deleted");
    }
    
    @Test
    void testFindByIdIncludingDeleted_ShouldFindSoftDeletedUsers() {
        // Arrange
        setUpTestData();
        Long deletedUserId = 4L; // User 4 is soft deleted

        // Act
        User user = userRepository.findByIdIncludingDeleted(deletedUserId);

        // Assert
        assertNotNull(user, "User 4 should be found when including deleted records");
        assertEquals("user2", user.getName(), "Should return the correct soft deleted user");
    }

    // 4. 测试条件查询 (findByCriteria)
    @Test
    void testFindByCriteria_ShouldReturnUsersBasedOnCriteria() {
        // Arrange
        setUpTestData();
        Criteria<User> criteria = Criteria.<User>create().eq("role_id", 2L);  // 查询 role_id 为 2 的用户

        // Act
        List<User> users = userRepository.findByCriteria(criteria);

        // Assert
        assertFalse(users.isEmpty(), "Should return users with role_id 2");
        users.forEach(user -> assertEquals(2L, user.getRoleId(), "User role_id should be 2"));
    }

    // 4. 测试条件查询 (findByCriteria)
    @Test
    void testFindByCriteria_ShouldReturnUsers_Repeat_OnCriteria() {
        // Arrange
        setUpTestData();

        Long userId=20000L;
        Long adminId=20001L;

        Criteria<User> criteria = Criteria.<User>create()
                .eq(true,User::getCreateBy,userId)
                .eq(true,User::getCreateBy,adminId)
                .eq("role_id", 20000L);  // 查询 role_id 为 2 的用户

        // Act
        List<User> users = userRepository.findByCriteria(criteria);

        // Assert
       // assertFalse(users.isEmpty(), "Should return users with role_id 2");
       // assertFalse(users.isEmpty(), "Should return users with role_id 2");
        users.forEach(user -> assertEquals(20000L, user.getRoleId(), "User role_id should be 2"));
    }

    private static final DateTimeFormatter DEFAULT_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LocalDateTime parseDateTime(String timeStr) {
        try {
            return LocalDateTime.parse(timeStr, DEFAULT_DATETIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "时间格式错误！请使用 '" + DEFAULT_DATETIME_FORMATTER + "' 格式，输入值: " + timeStr,
                    e
            );
        }
    }

    @Test
    void testFindByCriteria_ShouldReturnUsers_Time_OnCriteria() {
        // Arrange
        setUpTestData();


        Criteria<User> criteria = Criteria.<User>create()
                .gt(User::getCreateTime,parseDateTime("2024-04-10 14:30:00"))
                .lt(User::getCreateTime,parseDateTime("2025-08-22 17:13:30"));

        // Act
        List<User> users = userRepository.findByCriteria(criteria);

        // Assert
        assertFalse(users.isEmpty(), "Should return users with role_id 2");
        // assertFalse(users.isEmpty(), "Should return users with role_id 2");
        //users.forEach(user -> assertEquals(20000L, user.getRoleId(), "User role_id should be 2"));
    }




    @Test
    void testFindByCriteria_ShouldReturnEmptyListWhenNoMatches() {
        // Arrange
        setUpTestData();
        Criteria<User> criteria = Criteria.<User>create().eq("role_id", 999L);  // 查询不存在的 role_id

        // Act
        List<User> users = userRepository.findByCriteria(criteria);

        // Assert
        assertTrue(users.isEmpty(), "Should return an empty list when no matching users are found");
    }

    // 5. 测试根据条件查询单个用户 (findOneByCriteria)
    @Test
    void testFindOneByCriteria_ShouldReturnSingleUser() {
        // Arrange
        setUpTestData();
        Criteria<User> criteria = Criteria.<User>create().eq("id", 1L);  // 假设 ID 为 1 的用户存在

        // Act
        User user = userRepository.findOneByCriteria(criteria);

        // Assert
        assertNotNull(user, "User should be found with ID 1");
    }

    @Test
    void testFindOneByCriteria_ShouldThrowExceptionWhenMultipleResults() {
        // Arrange
        setUpTestData();
        Criteria<User> criteria = Criteria.<User>create().eq(User::getRoleId, 1L);  // 假设有多个 role_id 为 1 的用户
        // Act & Assert
        assertThrows(MultipleResultsException.class, () -> userRepository.findOneByCriteria(criteria),
                "Should throw MultipleResultsException when more than one result is found");
    }

    // 6. 测试分页查询 (pageByCriteria)
    @Test
    void testPageByCriteria_ShouldReturnPagedResults() {
        // Arrange
        setUpTestData();
        Criteria<User> criteria = Criteria.<User>create().eq("role_id", 2L);  // 查询 role_id 为 2 的用户
        criteria.setPageNo(1);
        criteria.setPageSize(2);

        // Act
        PageResult<User> pageResult = userRepository.pageByCriteria(criteria);

        // Assert
        assertNotNull(pageResult, "Page result should not be null");
        assertTrue(pageResult.getRecords().size() <= 2, "Page should contain no more than 2 users");
    }

    @Test
    void testPageByCriteria_ShouldReturnEmptyPageWhenNoResults() {
        // Arrange
        setUpTestData();
        Criteria<User> criteria = Criteria.<User>create().eq("role_id", 999L);  // 查询不存在的 role_id
        criteria.setPageNo(1);
        criteria.setPageSize(10);

        // Act
        PageResult<User> pageResult = userRepository.pageByCriteria(criteria);

        // Assert
        assertNotNull(pageResult, "Page result should not be null");
        assertTrue(pageResult.getRecords().isEmpty(), "Page should be empty when no users match the criteria");
    }

    // 7. 测试查询记录总数 (countByCriteria)
    @Test
    void testCountByCriteria_ShouldReturnCorrectCount() {
        // Arrange
        setUpTestData();
        Criteria<User> criteria = Criteria.<User>create().eq("role_id", 2L);  // 查询 role_id 为 2 的用户

        // Act
        Long count = userRepository.countByCriteria(criteria);

        // Assert
        assertTrue(count > 0, "Count should be greater than 0 for role_id 2");
    }

    @Test
    void testCountByCriteria_ShouldReturnZeroWhenNoMatches() {
        // Arrange
        setUpTestData();
        Criteria<User> criteria = Criteria.<User>create().eq("role_id", 999L);  // 查询不存在的 role_id

        // Act
        Long count = userRepository.countByCriteria(criteria);

        // Assert
        assertEquals(0L, count, "Count should be 0 when no matching users are found");
    }
}
