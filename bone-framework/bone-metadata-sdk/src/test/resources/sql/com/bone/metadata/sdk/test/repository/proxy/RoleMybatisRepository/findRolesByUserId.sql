SELECT u.*, r.role_name, r.description as role_description
FROM users u
LEFT JOIN roles r ON u.role_id = r.id
WHERE u.deleted = 0
<if test="name != null and name != ''">
    AND u.name LIKE CONCAT('%', :name, '%')
</if>
<if test="roleId != null">
    AND u.role_id = :roleId
</if>
ORDER BY u.create_time DESC