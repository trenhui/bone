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
    private Long id;
    private String name;
    private String code;
    private int level;
    private int status;
    private String adminEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Tenant create(Long id, String name, String code, int level, String adminEmail) {
        Tenant tenant = new Tenant();
        tenant.id = id;
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
}
