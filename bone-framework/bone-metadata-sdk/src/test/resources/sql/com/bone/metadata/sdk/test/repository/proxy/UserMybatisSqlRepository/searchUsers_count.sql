SELECT COUNT(*)
FROM users u
LEFT JOIN roles r ON u.role_id = r.id
WHERE u.deleted = 0
<if test="name != null and name != ''">
    AND u.name LIKE CONCAT('%', :name, '%')
</if>
<if test="roleId != null">
    AND u.role_id = :roleId
</if>
<if test="roleIds != null and roleIds.size() > 0">
    AND u.role_id IN (
        <foreach collection="roleIds" item="roleId" separator=",">
            :roleId
        </foreach>
    )
</if>