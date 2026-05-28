package com.bone.iam.adapter.web.dto.resp;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** {@code GET /api/v1/iam/me} 响应：当前登录账号的安全脱敏视图。 */
@Data
public class MeResp {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String realName;
    private String avatarUrl;
    private Integer status;
    private Boolean isAdmin;
    private Long tenantId;
    private List<String> scopes;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordUpdatedAt;
}
