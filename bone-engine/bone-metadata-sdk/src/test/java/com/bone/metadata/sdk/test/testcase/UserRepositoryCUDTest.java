package com.bone.metadata.sdk.test.testcase;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.repository.impl.UserRepositoryImpl;
import com.bone.metadata.sdk.test.utils.TestDataHelper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
@Transactional
public class UserRepositoryCUDTest {

  private NamedParameterJdbcOperations jdbc;

  private UserRepositoryImpl userRepository;

  @Autowired
  public UserRepositoryCUDTest(
      NamedParameterJdbcOperations jdbc, UserRepositoryImpl userRepository) {
    this.jdbc = jdbc;
    this.userRepository = userRepository;
  }

  @BeforeEach
  void setUp() {
    TestDataHelper.cleanTestData(jdbc);
    TestDataHelper.setUpTestData(jdbc);
  }

  // Helper method to create a test User instance
  private User createTestUser(String name, Long roleId, Long createdBy, boolean deleted) {
    User user = new User();
    user.setId(DistributedIdGenerator.generateLongId());
    user.setName(name);
    user.setRoleId(roleId);
    user.setCreatedAt(Timestamp.from(Instant.now()));
    user.setCreatedBy(createdBy);
    user.setUpdatedAt(Timestamp.from(Instant.now()));
    user.setUpdatedBy(createdBy);
    user.setDeleted(deleted);
    return user;
  }

  // 1. Test insert operation
  @Test
  void testInsert_ShouldCreateUserWithGeneratedId() {
    // Arrange
    User user = createTestUser("NewUser", 1L, 1001L, false);

    // Act
    Long userId = userRepository.insert(user);

    // Assert
    User insertedUser = userRepository.findById(userId);
    assertNotNull(insertedUser, "Inserted user should exist");
    assertEquals(user.getName(), insertedUser.getName(), "Name should match");
    assertEquals(user.getRoleId(), insertedUser.getRoleId(), "Role ID should match");
    assertNotNull(insertedUser.getCreatedAt(), "Create time should be set");
    assertEquals(false, insertedUser.getDeleted(), "User should not be soft deleted");
  }

  // 2. Test batchInsert operation
  @Test
  void testBatchInsert_ShouldInsertMultipleUsers() {
    // Arrange
    List<User> users =
        Arrays.asList(
            createTestUser("BatchUser1", 2L, 1002L, false),
            createTestUser("BatchUser2", 2L, 1002L, false),
            createTestUser("BatchUser3", 3L, 1003L, false));

    // Act
    userRepository.batchInsert(users);

    // Assert
    Criteria<User> criteria =
        Criteria.<User>create().in("name", Arrays.asList("BatchUser1", "BatchUser2", "BatchUser3"));
    List<User> insertedUsers = userRepository.findByCriteria(criteria);
    assertEquals(3, insertedUsers.size(), "Should insert 3 users");
    insertedUsers.forEach(
        user -> assertEquals(false, user.getDeleted(), "Users should not be soft deleted"));
  }

  @Test
  void testBatchInsert_ShouldHandleEmptyList() {
    // Arrange
    List<User> emptyList = Collections.emptyList();

    // Act
    userRepository.batchInsert(emptyList);

    // Assert
    Criteria<User> criteria = Criteria.<User>create().eq("name", "NonExistent");
    List<User> users = userRepository.findByCriteria(criteria);
    assertTrue(users.isEmpty(), "No users should be inserted");
  }

  // ADR-0019：insert() 尊重非空 id（空才生成，非空则尊重）
  @Test
  void testInsert_ShouldRespectPresetNonNullId() {
    // Arrange：预分配 id（模拟应用层构造期确定身份）
    Long presetId = DistributedIdGenerator.generateLongId();
    User user = new User();
    user.setId(presetId);
    user.setName("PresetIdUser");
    user.setRoleId(1L);
    user.setCreatedAt(Timestamp.from(Instant.now()));
    user.setCreatedBy(1001L);
    user.setUpdatedAt(Timestamp.from(Instant.now()));
    user.setUpdatedBy(1001L);
    user.setDeleted(false);

    // Act
    Long returnedId = userRepository.insert(user);

    // Assert：返回值与内存态 id 均为预分配值，未被静默覆盖
    assertEquals(presetId, returnedId, "insert() should return the preset id");
    assertEquals(presetId, user.getId(), "in-memory id should stay the preset id");
    User insertedUser = userRepository.findById(presetId);
    assertNotNull(insertedUser, "Row should be persisted under the preset id");
    assertEquals("PresetIdUser", insertedUser.getName(), "Name should match");
  }

  // ADR-0019：id 为空时仍由 SDK 生成（存量行为不变）
  @Test
  void testInsert_ShouldGenerateIdWhenNull() {
    // Arrange
    User user = createTestUser("NullIdUser", 1L, 1001L, false);
    user.setId(null);

    // Act
    Long returnedId = userRepository.insert(user);

    // Assert
    assertNotNull(returnedId, "insert() should generate an id when it is null");
    assertEquals(returnedId, user.getId(), "in-memory id should be the generated id");
    assertNotNull(userRepository.findById(returnedId), "Row should be persisted");
  }

  // 3. Test save operation (insert new user)
  @Test
  void testSave_ShouldInsertNewUser() {
    // Arrange
    User user = createTestUser("SaveNewUser", 1L, 1001L, false);

    // Act
    Long userId = userRepository.save(user);

    // Assert
    User savedUser = userRepository.findById(userId);
    assertNotNull(savedUser, "Saved user should exist");
    assertEquals(user.getName(), savedUser.getName(), "Name should match");
    assertEquals(user.getRoleId(), savedUser.getRoleId(), "Role ID should match");
    assertNotNull(savedUser.getCreatedAt(), "Create time should be set");
  }

  // 4. Test save operation (update existing user)
  @Test
  void testSave_ShouldUpdateExistingUser() {
    // Arrange
    User user = createTestUser("InitialUser", 1L, 1001L, false);
    Long userId = userRepository.insert(user);
    User updatedUser = userRepository.findById(userId);
    updatedUser.setName("UpdatedUser");
    updatedUser.setRoleId(2L);
    updatedUser.setUpdatedBy(1002L);
    updatedUser.setUpdatedAt(Timestamp.from(Instant.now()));

    // Act
    userRepository.save(updatedUser);

    // Assert
    User savedUser = userRepository.findById(userId);
    assertNotNull(savedUser, "Updated user should exist");
    assertEquals("UpdatedUser", savedUser.getName(), "Name should be updated");
    assertEquals(2L, savedUser.getRoleId(), "Role ID should be updated");
    assertEquals(1002L, savedUser.getUpdatedBy(), "Update by should be updated");
  }

  // 5. Test batchSave operation
  @Test
  void testBatchSave_ShouldInsertAndUpdateUsers() {
    // Arrange
    User cuser = createTestUser("AuditUser", 1L, 1001L, false);
    userRepository.save(cuser);
    // Arrange
    User existingUser = userRepository.findById(cuser.getId());
    existingUser.setName("UpdatedExistingUser");
    User newUser = createTestUser("NewBatchUser", 3L, 1003L, false);
    List<User> users = Arrays.asList(existingUser, newUser);

    // Act
    userRepository.batchSave(users);

    // Assert
    User updatedUser = userRepository.findById(cuser.getId());
    assertNotNull(updatedUser, "Existing user should be updated");
    assertEquals(
        "UpdatedExistingUser", updatedUser.getName(), "Existing user name should be updated");

    Criteria<User> criteria = Criteria.<User>create().eq("name", "NewBatchUser");
    List<User> insertedUsers = userRepository.findByCriteria(criteria);
    assertFalse(insertedUsers.isEmpty(), "New user should be inserted");
    assertEquals(3L, insertedUsers.get(0).getRoleId(), "New user role ID should match");
  }

  // 6. Test update operation
  @Test
  void testUpdate_ShouldUpdateUserFields() {
    // Arrange
    createTestUser("dd", 1L, 1001L, false);
    User cuser = createTestUser("testUpdate_ShouldUpdateUserFields", 2L, 1002L, false);
    userRepository.insert(cuser);
    User user = userRepository.findById(cuser.getId());
    user.setName("UpdatedUser");
    user.setRoleId(2L);
    user.setUpdatedBy(1002L);

    // Act
    boolean result = userRepository.update(user);

    // Assert
    assertTrue(result, "Update should succeed");
    User updatedUser = userRepository.findById(cuser.getId());
    assertEquals("UpdatedUser", updatedUser.getName(), "Name should be updated");
    assertEquals(2L, updatedUser.getRoleId(), "Role ID should be updated");
    assertEquals(1002L, updatedUser.getUpdatedBy(), "Update by should be updated");
  }

  @Test
  void testUpdate_ShouldReturnFalseForNonExistentUser() {
    // Arrange
    User user = createTestUser("NonExistent", 1L, 1001L, false);
    user.setId(999L);

    // Act
    boolean result = userRepository.update(user);

    // Assert
    assertFalse(result, "Update should fail for non-existent user");
  }

  // 7. Test updateByCriteria operation
  @Test
  void testUpdateByCriteria_ShouldUpdateMatchingUsers() {
    // Arrange
    User updateTemplate = new User();
    updateTemplate.setName("BatchUpdatedUser");
    updateTemplate.setUpdatedBy(1002L);
    Criteria<User> criteria = Criteria.<User>create().eq("role_id", 1L);

    // Act
    int updatedRows = userRepository.updateByCriteria(updateTemplate, criteria);

    // Assert
    assertTrue(updatedRows > 0, "Should update multiple users");
    List<User> updatedUsers = userRepository.findByCriteria(criteria);
    updatedUsers.forEach(
        user -> {
          assertEquals("BatchUpdatedUser", user.getName(), "Name should be updated");
          assertEquals(1002L, user.getUpdatedBy(), "Update by should be updated");
        });
  }

  @Test
  void testUpdateByCriteria_ShouldReturnZeroForNoMatches() {
    // Arrange
    User updateTemplate = new User();
    updateTemplate.setName("NoMatchUser");
    Criteria<User> criteria = Criteria.<User>create().eq("role_id", 999L);

    // Act
    int updatedRows = userRepository.updateByCriteria(updateTemplate, criteria);

    // Assert
    assertEquals(0, updatedRows, "No rows should be updated");
  }

  // 8. Test deleteById operation
  @Test
  void testDeleteById_ShouldSoftDeleteUser() {
    TestDataHelper.setUpTestData(jdbc);
    // Arrange

    User user = createTestUser("NewUser", 1L, 1001L, false);
    userRepository.insert(user);

    Long userId = user.getId();
    // Act
    boolean result = userRepository.deleteById(userId);

    // Assert
    assertTrue(result, "Delete should succeed");
    User deletedUser = userRepository.findById(userId);
    assertNull(deletedUser, "Soft deleted user should not be found");
    User rawUser = userRepository.findByIdIncludingDeleted(userId);
    assertNotNull(rawUser, "User should exist in raw query");
    assertEquals(true, rawUser.getDeleted(), "User should be soft deleted");
  }

  @Test
  void testDeleteById_ShouldReturnFalseForNonExistentUser() {
    // Arrange
    Long nonExistentId = 999L;

    // Act
    userRepository.deleteById(nonExistentId);

    // Assert
    // assertFalse(result, "Delete should fail for non-existent user");
  }

  // 9. Test deleteByIds operation
  @Test
  void testDeleteByIds_ShouldSoftDeleteMultipleUsers() {
    // Arrange
    List<Long> ids = Arrays.asList(1L, 2L);

    // Act
    userRepository.deleteByIds(ids);

    // Assert
    List<User> deletedUsers = userRepository.findByIds(ids);
    assertTrue(deletedUsers.isEmpty(), "Soft deleted users should not be found");
    Criteria<User> criteria = Criteria.<User>create().in("id", ids);
    List<User> rawUsers = userRepository.findByCriteria(criteria);
    rawUsers.forEach(user -> assertEquals(1, user.getDeleted(), "Users should be soft deleted"));
  }

  @Test
  void testDeleteByIds_ShouldHandleEmptyList() {
    // Arrange
    List<Long> emptyIds = Collections.emptyList();

    // Act
    userRepository.deleteByIds(emptyIds);

    // Assert
    Criteria<User> criteria = Criteria.<User>create().eq(User::getName, "admin1");
    List<User> users = userRepository.findByCriteria(criteria);
    assertFalse(users.isEmpty(), "No users should be affected");
  }

  // 10. Test timestamp and audit fields
  @Test
  void testInsert_ShouldSetAuditFields() {
    // Arrange
    User user = createTestUser("AuditUser", 1L, 1001L, false);

    // Act
    Long userId = userRepository.insert(user);

    // Assert
    User insertedUser = userRepository.findById(userId);
    assertNotNull(insertedUser.getCreatedAt(), "Create time should be set");
    assertNotNull(insertedUser.getUpdatedAt(), "Update time should be set");
    assertEquals(1001L, insertedUser.getCreatedBy(), "Create by should match");
    assertEquals(1001L, insertedUser.getUpdatedBy(), "Update by should match");
  }

  @Test
  void testUpdate_ShouldUpdateAuditFields() {
    User cuser = createTestUser("AuditUser", 1L, 1001L, false);
    userRepository.save(cuser);
    // Arrange
    User user = userRepository.findById(cuser.getId());
    user.setName("AuditUpdatedUser");
    user.setUpdatedBy(1002L);
    user.setUpdatedAt(new Timestamp(user.getCreatedAt().getTime() + 1000)); // +1秒

    // Act
    userRepository.update(user);

    // Assert
    User updatedUser = userRepository.findById(cuser.getId());
    assertEquals(1002L, updatedUser.getUpdatedBy(), "Update by should be updated");
    assertTrue(
        updatedUser.getUpdatedAt().after(updatedUser.getCreatedAt()),
        "Update time should be later than create time");
  }
}
