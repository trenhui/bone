package com.bone.blueprint.domain.model.dashboard;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Widget extends AggregateRoot<Long> {
    private Long dashboardId;
    private String title;
    private String type;
    private String config;
    private int positionX;
    private int positionY;
    private int width;
    private int height;
    
    public static Widget create(Long dashboardId, String title, String type, String config, int positionX, int positionY, int width, int height) {
        Widget widget = new Widget();
        widget.dashboardId = dashboardId;
        widget.title = title;
        widget.type = type;
        widget.config = config;
        widget.positionX = positionX;
        widget.positionY = positionY;
        widget.width = width;
        widget.height = height;
        return widget;
    }
    
    public void update(String title, String type, String config, int positionX, int positionY, int width, int height) {
        this.title = title;
        this.type = type;
        this.config = config;
        this.positionX = positionX;
        this.positionY = positionY;
        this.width = width;
        this.height = height;
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
