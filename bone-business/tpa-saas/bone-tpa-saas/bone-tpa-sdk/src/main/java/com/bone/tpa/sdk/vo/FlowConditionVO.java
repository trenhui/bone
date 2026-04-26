package com.bone.tpa.sdk.vo;

import lombok.Builder;

public class FlowConditionVO {
    /**
     * 规则描述
     */
    private String name ;
    /**
     * 实现的condition的名字
     */
    private String beanName ;
    /**
     * 阶段
     * ClaimStageEnum.code
     */

    /**
     * 0 无效
     * 1 生效
     */
    private Integer status = 0;

    /**
     * 类型
     * 0 普通条件
     * 1 脚本条件
     *
     */
    private Integer type=0;

    /**
     * js 的脚本内容
     *
     * 实现这样的 函数，context 可以扩展里面包含的内容，根据业务需要
     * function excute(Context context){
     *     return true ;
     * }
     */
    private String script="";



    static public FlowConditionVO normalCondition(String name ,String beanName){
        FlowConditionVO rs = new FlowConditionVO();
        rs.setName(name);
        rs.setBeanName(beanName);
        return rs;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
