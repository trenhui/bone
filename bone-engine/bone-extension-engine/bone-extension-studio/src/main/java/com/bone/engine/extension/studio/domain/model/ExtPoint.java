package com.bone.engine.extension.studio.domain.model;

import com.bone.core.domain.entity.Entity;

public class ExtPoint extends Entity<Long> {
    
    private String name;
    private String description;
    private String interfaceName;
    private String domain;
    private String category;
    private boolean enabled;
    
    // Getter methods
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getInterfaceName() {
        return interfaceName;
    }
    
    public String getDomain() {
        return domain;
    }
    
    public String getCategory() {
        return category;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    // Setter methods
    public void setName(String name) {
        this.name = name;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}