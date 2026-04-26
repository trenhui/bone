package com.bone.tpa.facade.vo;

import lombok.Data;

import java.util.List;

@Data
public class PkbImageResponse {

    private String claimNumber;


    private List<PkbImageVO> images;
}
