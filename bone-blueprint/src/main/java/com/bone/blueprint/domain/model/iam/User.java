package com.bone.blueprint.domain.model.iam;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends AggregateRoot<Long> {
    private String username;
    private String email;
    private String passwordHash;
    private String status;
    private Long tenantId;
    
    public static User create(String username, String email, String passwordHash, Long tenantId) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        user.status = "ENABLED";
        user.tenantId = tenantId;
        return user;
    }
    
    public void update(String email, String status) {
        this.email = email;
        this.status = status;
    }
    
    public void disable() {
        this.status = "DISABLED";
    }
    
    public void enable() {
        this.status = "ENABLED";
    }
    
    public void resetPassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
