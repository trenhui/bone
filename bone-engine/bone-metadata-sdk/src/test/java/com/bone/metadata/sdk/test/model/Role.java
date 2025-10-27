package com.bone.metadata.sdk.test.model;

/**
 * 角色实体类
 */
public class Role {
    private Long id;
    private String name;
    private String code;
    
    /**
     * 获取ID
     */
    public Long getId() {
        return id;
    }
    
    /**
     * 设置ID
     */
    public void setId(Long id) {
        this.id = id;
    }
    
    /**
     * 获取名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 设置名称
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * 获取代码
     */
    public String getCode() {
        return code;
    }
    
    /**
     * 设置代码
     */
    public void setCode(String code) {
        this.code = code;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Role role = (Role) o;
        
        if (id != null ? !id.equals(role.id) : role.id != null) return false;
        if (name != null ? !name.equals(role.name) : role.name != null) return false;
        return code != null ? code.equals(role.code) : role.code == null;
    }
    
    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (code != null ? code.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                '}';
    }
}