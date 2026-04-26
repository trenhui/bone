package com.bone.tpa.sdk.vo;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class JumpTypeVO {
    static public final String IN_TYPE = "0";
    static public final String OUT_TYPE = "1";
    static public final String CONDITION_AND_TYPE = "2";
    static public final String CONDITION_OR_TYPE = "3";


    private String name ;
    private String type=IN_TYPE;

    /**
     * 0 无效 1 有效
     */
    private Integer status=0;
    /**
     * 条件列表
     */
    private List<FlowConditionVO> conditionList = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<FlowConditionVO> getConditionList() {
        return conditionList;
    }

    public void setConditionList(List<FlowConditionVO> conditionList) {
        this.conditionList = conditionList;
    }



    public FlowConditionVO getConditionByBeanName(String beanName){
        if( conditionList == null){
            return  null;
        }
        for (FlowConditionVO condition : conditionList) {
            if(StringUtils.equals(condition.getBeanName(),beanName)){
                return condition;
            }
        }
        return null;
    }

    static public JumpTypeVO getInConditionVo(){
        JumpTypeVO inType = new JumpTypeVO();
        inType.setName("进入");
        inType.setType(IN_TYPE);
        inType.setStatus(0);
        return inType;
    }
    static public JumpTypeVO getOutConditionVo() {
        JumpTypeVO outType = new JumpTypeVO();
        outType.setName("跳过");
        outType.setType(OUT_TYPE);
        outType.setStatus(0);
        return outType;
    }

    static public JumpTypeVO getDefaultAndConditionVo(){
        JumpTypeVO conditionAndType = new JumpTypeVO();
        conditionAndType.setName("符合所有条件进入");
        conditionAndType.setType(CONDITION_AND_TYPE);
        conditionAndType.setStatus(0);
        return conditionAndType;
    }

    static public JumpTypeVO getDefaultOrConditionVo(){
        JumpTypeVO conditionOrType = new JumpTypeVO();
        conditionOrType.setName("符合任一条件进入");
        conditionOrType.setType(CONDITION_OR_TYPE);
        conditionOrType.setStatus(0);
        return conditionOrType;
    }



}
