package com.bone.iam.domain.tenant;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_tenant")
public class Tenant extends AggregateRoot<Long> {
    private String name;
    private String code;
    private int level;
    private int status;
    private String adminEmail;
    /** 账号配额，{@code null} 表示不限制（社区版 As-Is 列）。 */
    private Integer maxAccounts;
    /** 角色配额，{@code null} 表示不限制。 */
    private Integer maxRoles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Tenant create(Long id, String name, String code, int level, String adminEmail) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.name = name;
        tenant.code = code;
        tenant.level = level;
        tenant.status = 1;
        tenant.adminEmail = adminEmail;
        tenant.createdAt = LocalDateTime.now();
        tenant.updatedAt = LocalDateTime.now();
        return tenant;
    }

    public void update(String name, String adminEmail, int level) {
        this.name = name;
        this.adminEmail = adminEmail;
        this.level = level;
        this.updatedAt = LocalDateTime.now();
    }

    public void enable() {
        this.status = 1;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        this.status = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateQuota(Integer maxAccounts, Integer maxRoles) {
        this.maxAccounts = maxAccounts;
        this.maxRoles = maxRoles;
        this.updatedAt = LocalDateTime.now();
    }
}
