package com.bone.metadata.sdk.test.repository.proxy;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.SqlFragment;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.domain.dto.UserRoleDTO;
import com.bone.metadata.sdk.test.domain.dto.UserWithRoleDTO;
import com.bone.metadata.sdk.test.domain.query.UserPageQuery;
import com.bone.metadata.sdk.test.domain.query.UserQuery;
import com.bone.metadata.sdk.test.domain.request.UserSearchRequest;

import java.util.List;

/**
 * 用户数据访问接口 - 完全兼容 MyBatis 语法
 */
@SqlFragment(id = "userColumns",
        value = "u.id, u.name, u.role_id, u.create_time, u.create_by, u.update_time, u.update_by, u.deleted")
@SqlFragment(id = "userWithRoleColumns",
        value = "u.id, u.name, u.role_id, u.create_time, u.create_by, u.update_time, u.update_by, u.deleted, " +
                "r.role_name, r.description AS role_description")
public interface UserMybatisSqlRepository extends Repository<User, Long> {

    /**
     * 搜索用户（带分页和多重条件）
     */
    @Sql("SELECT <include refid=\"userWithRoleColumns\"/> FROM users u LEFT JOIN roles r ON u.role_id = r.id WHERE u.deleted = 0 <if test=\"request.name != null and request.name != ''\"> AND u.name LIKE CONCAT('%', #{request.name}, '%') </if> <if test=\"request.roleId != null\"> AND u.role_id = #{request.roleId} </if> <if test=\"request.roleIds != null and !request.roleIds.isEmpty()\"> AND u.role_id IN <foreach collection=\"request.roleIds\" item=\"item\" open=\"(\" close=\")\" separator=\",\"> #{item} </foreach> </if> <if test=\"request.pageNumber != null and request.pageSize != null\"> ORDER BY u.create_time DESC LIMIT #{request.pageSize} OFFSET #{request.offset} </if>")
    List<UserWithRoleDTO> searchUsers(@Param("request") UserSearchRequest request);

    /**
     * 根据姓名模糊查询用户
     */
    @Sql("SELECT <include refid='userColumns'/> FROM users u WHERE u.name LIKE CONCAT('%', #{name}, '%') AND u.deleted = 0")
    List<User> findByName(@Param("name") String name);

    /**
     * 查询用户权限（分页）
     */
    @Sql("""
                SELECT <include refid="userWithRoleColumns"/>
                FROM users u 
                LEFT JOIN roles r ON u.role_id = r.id 
                <where>
                    u.deleted = 0
                    <if test="userPageQuery.name != null and userPageQuery.name != ''">
                        AND u.name LIKE CONCAT('%', #{userPageQuery.name}, '%')
                    </if>
                    <if test="userPageQuery.roleId != null">
                        AND u.role_id = #{userPageQuery.roleId}
                    </if>
                </where>
                ORDER BY u.create_time DESC
                <if test="userPageQuery.page != null and userPageQuery.pageSize != null">
                    LIMIT #{userPageQuery.pageSize} OFFSET #{userPageQuery.page}
                </if>
            """)
    PageResult<UserRoleDTO> queryUerPermPage(@Param("userPageQuery") UserPageQuery userPageQuery);

    List<UserWithRoleDTO> findUsersWithRole(@Param("name") String name, @Param("roleId") Long roleId);

    /**
     * 查询用户
     */
    @Sql("""
                SELECT <include refid="userColumns"/>
                FROM users u
                <where>
                    u.deleted = 0
                    <if test="query.name != null and query.name != ''">
                        AND u.name LIKE CONCAT('%', #{query.name}, '%')
                    </if>
                    <if test="query.roleId != null">
                        AND u.role_id = #{query.roleId}
                    </if>
                </where>
                ORDER BY u.create_time DESC
                <if test="query.page != null and query.pageSize != null">
                    LIMIT #{query.pageSize} OFFSET #{query.page}
                </if>
            """)
    PageResult<User> queryUsers(@Param("query") UserQuery query);

    /**
     * 使用片段查询（表名需外部验证）
     */
    List<User> queryWithFragment(@Param("tableName") String tableName, @Param("status") Integer status);

    /**
     * 更新用户姓名
     */
    @Sql("""
                UPDATE users 
                <set>
                    <if test="name != null and name != ''">
                        name = #{name},
                    </if>
                    update_time = NOW(),
                    update_by = #{updateBy}
                </set>
                WHERE id = #{id} AND deleted = 0
            """)
    int updateName(@Param("id") Long id, @Param("name") String name, @Param("updateBy") Long updateBy);

    /**
     * 软删除用户
     */
    @Sql("""
                UPDATE users 
                <set>
                    deleted = 1,
                    update_time = NOW(),
                    update_by = #{updateBy}
                </set>
                WHERE id = #{id}
            """)
    int deleteById(@Param("id") Long id, @Param("updateBy") Long updateBy);

    /**
     * 根据角色ID查询用户
     */
    @Sql("SELECT <include refid='userColumns'/> FROM users u WHERE u.role_id = #{roleId} AND u.deleted = 0")
    List<User> findByRoleId(@Param("roleId") Long roleId);

    /**
     * 插入用户
     */
    @Sql("INSERT INTO users (name, role_id, create_time, create_by, deleted) VALUES (#{name}, #{roleId}, NOW(), #{createBy}, 0)")
    void insertUser(@Param("name") String name, @Param("roleId") Long roleId, @Param("createBy") Long createBy);

    /**
     * 获取最后插入的ID
     */
    @Sql("SELECT LAST_INSERT_ID()")
    Long getLastInsertId();
}
