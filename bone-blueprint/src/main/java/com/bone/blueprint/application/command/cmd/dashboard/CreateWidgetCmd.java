package com.bone.blueprint.application.command.cmd.dashboard;

public class CreateWidgetCmd {
    private Long dashboardId;
    private String title;
    private String type;
    private String config;
    private int positionX;
    private int positionY;
    private int width;
    private int height;
    
    // Getters and setters
    public Long getDashboardId() {
        return dashboardId;
    }
    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getConfig() {
        return config;
    }
    public void setConfig(String config) {
        this.config = config;
    }
    public int getPositionX() {
        return positionX;
    }
    public void setPositionX(int positionX) {
        this.positionX = positionX;
    }
    public int getPositionY() {
        return positionY;
    }
    public void setPositionY(int positionY) {
        this.positionY = positionY;
    }
    public int getWidth() {
        return width;
    }
    public void setWidth(int width) {
        this.width = width;
    }
    public int getHeight() {
        return height;
    }
    public void setHeight(int height) {
        this.height = height;
    }
}
