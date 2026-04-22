package com.bone.blueprint.domain.model.dashboard;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QuickAccess extends AggregateRoot<Long> {
    private Long userId;
    private String name;
    private String url;
    private String icon;
    private int order;
    
    public static QuickAccess create(Long userId, String name, String url, String icon, int order) {
        QuickAccess quickAccess = new QuickAccess();
        quickAccess.userId = userId;
        quickAccess.name = name;
        quickAccess.url = url;
        quickAccess.icon = icon;
        quickAccess.order = order;
        return quickAccess;
    }
    
    public void update(String name, String url, String icon, int order) {
        this.name = name;
        this.url = url;
        this.icon = icon;
        this.order = order;
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
