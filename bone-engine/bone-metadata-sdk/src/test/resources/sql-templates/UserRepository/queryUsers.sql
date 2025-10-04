-- resources/sql/com/example/UserRepository/queryUsers.sql
SELECT u.id, u.name, u.email, r.role_name
FROM ${tableName} u
LEFT JOIN roles r ON u.role_id = r.id
<where>
    <if test="name != null and _sql.isNotEmpty(name)">
        AND u.name LIKE CONCAT('%', :name, '%')
    </if>
    <if test="status != null">
        AND u.status = :status
    </if>
    <if test="roleNames != null and _sql.isNotEmpty(roleNames)">
        AND r.role_name IN (
            <foreach collection="roleNames" item="role" separator=",">
                :role
            </foreach>
        )
    </if>
</where>
<if test="orderBy != null and _sql.isNotEmpty(orderBy)">
    ORDER BY :orderBy
</if>