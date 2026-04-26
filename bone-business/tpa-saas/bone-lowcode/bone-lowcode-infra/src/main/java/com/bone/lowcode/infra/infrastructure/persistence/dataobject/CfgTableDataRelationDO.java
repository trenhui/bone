package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@TableName("cfg_table_data_relation")
public class CfgTableDataRelationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long pageId;

    /**
     * 在数据上存在一对多关系的两个表格中'多'方表格的id
     */
    private Long currentTableId;

    /**
     * 在数据上存在一对多关系的两个表格中'多'方表格的关系字段id
     */
    private Long currentRelationFieldId;

    /**
     * 在数据上存在一对多关系的两个表格中一'方表格的id
     */
    private Long targetTableId;

    /**
     * 在数据上存在一对多关系的两个表格中'一'方表格的关系字段id
     */
    private Long targetRelationFieldId;
}
