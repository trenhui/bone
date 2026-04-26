package com.bone.tpa.facade.request;


import lombok.Data;

@Data
public class TpaAddLogRequest {
    private String mockTag ;
    private Long claimNumber ;

    private String operation ;


    private String remark ;



    private String createBy ;

    private Long createTime ;

    private String updateBy ;


    private Long updateTime ;
}
