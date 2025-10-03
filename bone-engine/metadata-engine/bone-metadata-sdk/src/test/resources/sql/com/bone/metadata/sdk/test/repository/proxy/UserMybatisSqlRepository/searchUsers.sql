SELECT <include refid="userWithRoleColumns"/>
FROM users u
LEFT JOIN roles r ON u.role_id = r.id
WHERE u.deleted = 0
<if test="request.name != null and request.name != ''">
    AND u.name LIKE CONCAT('%', #{request.name}, '%')
</if>
<if test="request.roleId != null">
    AND u.role_id = #{request.roleId}
</if>
<if test="request.roleIds != null and !request.roleIds.isEmpty()">
    AND u.role_id IN (#{request.roleIds})
</if>
<if test="request.pageNumber != null and request.pageSize != null">
    ORDER BY u.create_time DESC
    LIMIT #{request.pageSize} OFFSET #{request.offset}
</if>