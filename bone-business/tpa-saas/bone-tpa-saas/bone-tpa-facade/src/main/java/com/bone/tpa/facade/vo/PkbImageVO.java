package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class PkbImageVO {

    private String imagePath; //影像件路径

    private String imageName; //影像件名称

    private Integer imageIndex; //影像件序号

    private String claimImageId; //主键id
}
