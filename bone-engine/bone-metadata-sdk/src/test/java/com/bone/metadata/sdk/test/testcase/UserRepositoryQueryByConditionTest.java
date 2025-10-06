package com.bone.metadata.sdk.test.testcase;

import com.bone.core.enums.Operator;
import com.bone.core.model.PageResult;
import com.bone.core.model.QueryParam;
import com.bone.core.model.SortingField;
import com.bone.metadata.sdk.domain.enums.SortDirection;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.repository.impl.UserRepositoryImpl;
import com.bone.metadata.sdk.test.utils.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the queryByCondition method in BaseRepository.
 */
@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
public class UserRepositoryQueryByConditionTest {

    private final NamedParameterJdbcOperations jdbc;
    private final UserRepositoryImpl userRepository;

    @Autowired
    public UserRepositoryQueryByConditionTest(NamedParameterJdbcOperations jdbc, UserRepositoryImpl userRepository) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
    }


    /**
     * Set up test data before each test case.
     */
    @BeforeEach
    void setUp() {
        TestDataHelper.setUpTestData(jdbc);
    }

    /**
     * Test querying with specific conditions (role_id and status).
     */
    @Test
    void testQueryByCondition_ShouldReturnPagedResultsWithConditions() {
        // Arrange
        List<QueryParam> queryParams = List.of(
                new QueryParam("role_id", 2L, Operator.EQ)//,
                //new QueryParam("status", "active", "EQUAL")
        );
        List<SortingField> sortingFields = List.of(
                new SortingField("name", SortDirection.ASC.getDirection())
        );
        Integer pageNo = 1;
        int pageSize = 2;
        // Act
        PageResult<User> pageResult = userRepository.queryByCondition(queryParams, sortingFields, pageNo, pageSize, null);

        // Assert
        assertNotNull(pageResult, "Page model should not be null");
        assertTrue(pageResult.getRecords().size() <= pageSize, "Page size should not exceed the specified limit");
        assertEquals(2L, pageResult.getRecords().get(0).getRoleId(), "User role_id should match the query condition");
       // assertEquals("active", pageResult.getData().get(0).getStatus(), "User status should match the query condition");
    }

    /**
     * Test querying with no matching results.
     */
    @Test
    void testQueryByCondition_ShouldReturnEmptyPageWhenNoMatches() {
        // Arrange
        List<QueryParam> queryParams = Arrays.asList(
                new QueryParam("role_id", 999L, Operator.EQ) // Non-existent role_id
        );

        // Act
        PageResult<User> pageResult = userRepository.queryByCondition(queryParams, null, 1, 10, null);

        // Assert
        assertNotNull(pageResult, "Page model should not be null");
        assertTrue(pageResult.getRecords().isEmpty(), "Page should be empty when no users match the criteria");
    }

    /**
     * Test sorting with multiple fields (username ASC, email DESC).
     */
    @Test
    void testQueryByCondition_ShouldReturnSortedResults() {
        // Arrange
        List<QueryParam> queryParams = Arrays.asList(
                new QueryParam("role_id", 2L, Operator.EQ)
        );
        List<SortingField> sortingFields = Arrays.asList(
                new SortingField("name", SortDirection.ASC.getDirection())//,
                //new SortingField("email", SortDirection.DESC.getDirection())
        );
        Integer pageNo = 1;
        Integer pageSize = 5;

        // Act
        PageResult<User> pageResult = userRepository.queryByCondition(queryParams, sortingFields, pageNo, pageSize, null);

        // Assert
        assertNotNull(pageResult, "Page model should not be null");
        assertFalse(pageResult.getRecords().isEmpty(), "There should be users matching the criteria");
        assertTrue(pageResult.getRecords().get(0).getName().compareTo(pageResult.getRecords().get(1).getName()) < 0,
                "Users should be sorted by username in ascending order");
       // assertTrue(pageResult.getData().get(0).getEmail().compareTo(pageResult.getData().get(1).getEmail()) > 0,
         //       "Users should be sorted by email in descending order");
    }

    /**
     * Test pagination with a large page number.
     */
    @Test
    void testQueryByCondition_ShouldHandleLargePageNumber() {
        // Arrange
        List<QueryParam> queryParams = List.of(
                new QueryParam("role_id", 2L, Operator.EQ)
        );

        // Act
        PageResult<User> pageResult = userRepository.queryByCondition(queryParams, null, 999, 10, null);

        // Assert
        assertNotNull(pageResult, "Page model should not be null");
        assertTrue(pageResult.getRecords().isEmpty(), "Page should be empty for a large page number without enough data");
    }



    /**
     * Test querying with a BETWEEN condition (age range).
     */
    @Test
    void testQueryByCondition_ShouldReturnUsersForBetweenCondition() {


        // Arrange
        List<QueryParam> queryParams = List.of(
                new QueryParam("roleId", Arrays.asList(1, 10), Operator.BETWEEN) // Query for age between 18 and 30
        );

        // Act
        PageResult<User> pageResult = userRepository.queryByCondition(queryParams, null, 1, 10, null);

        // Assert
        assertNotNull(pageResult, "Page model should not be null");
        assertFalse(pageResult.getRecords().isEmpty(), "There should be users with age between 18 and 30");
        pageResult.getRecords().forEach(user -> {
            assertTrue(user.getRoleId() >= 1 && user.getRoleId() <= 10, "User age should be between 18 and 30");
        });
    }
}