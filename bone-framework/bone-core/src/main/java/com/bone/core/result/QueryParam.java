package com.bone.core.result;

import com.bone.core.enums.Operator;
import lombok.Data;

@Data
public class QueryParam {

    /**
     * 查询的字段
     */
    private String field;

    /**
     * 查询的值
     */
    private Object value;

    /**
     * 查询的类型
     * 精确、模糊、大于、小于
     */
    private Operator type = Operator.EQ;

    public QueryParam() {
    }

    public QueryParam(String field, Object value) {
        this.field = field;
        this.value = value;
    }

    public QueryParam(String field, Object value, Operator type) {
        this.field = field;
        this.value = value;
        this.type = type;
    }
}
