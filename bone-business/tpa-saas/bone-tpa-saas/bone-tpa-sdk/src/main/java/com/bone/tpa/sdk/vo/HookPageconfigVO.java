package com.bone.tpa.sdk.vo;

import lombok.Data;

/**
 *
 */
public class HookPageconfigVO {

    private String domain;
    private String domainDesc;
    private String beanType ;
    private String beanTypeDesc;
    private String beanName ;
    private String beanDesc;
    private Integer status=0; // 1 启用 0 禁用

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDomainDesc() {
        return domainDesc;
    }

    public void setDomainDesc(String domainDesc) {
        this.domainDesc = domainDesc;
    }

    public String getBeanType() {
        return beanType;
    }

    public void setBeanType(String beanType) {
        this.beanType = beanType;
    }

    public String getBeanTypeDesc() {
        return beanTypeDesc;
    }

    public void setBeanTypeDesc(String beanTypeDesc) {
        this.beanTypeDesc = beanTypeDesc;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getBeanDesc() {
        return beanDesc;
    }

    public void setBeanDesc(String beanDesc) {
        this.beanDesc = beanDesc;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
