package com.bone.blueprint.domain.model.dashboard;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Dashboard extends AggregateRoot<Long> {
    private String name;
    private String description;
    private Long userId;
    private boolean isPublic;
    
    public static Dashboard create(String name, String description, Long userId, boolean isPublic) {
        Dashboard dashboard = new Dashboard();
        dashboard.name = name;
        dashboard.description = description;
        dashboard.userId = userId;
        dashboard.isPublic = isPublic;
        return dashboard;
    }
    
    public void update(String name, String description, boolean isPublic) {
        this.name = name;
        this.description = description;
        this.isPublic = isPublic;
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
