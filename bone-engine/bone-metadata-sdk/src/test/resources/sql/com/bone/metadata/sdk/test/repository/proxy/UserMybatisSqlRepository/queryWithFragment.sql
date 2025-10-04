SELECT <include refid="userColumns"/>
FROM ${tableName} u
WHERE u.deleted = #{status}
ORDER BY u.create_time DESC