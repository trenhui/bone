package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class TpaLogVO {

    private Long objectId;

    private String remark ;

    private String operation;

    private String createBy ;

    private Long createTime ;

    private String updateBy ;


    private Long updateTime ;
}
