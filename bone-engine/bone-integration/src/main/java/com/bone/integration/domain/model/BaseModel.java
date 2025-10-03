package com.bone.integration.domain.model;

import lombok.Data;

import java.util.Date;

@Data
public class BaseModel {
    private Date createdAt;
    private Long createdBy;
    private Date updatedAt;
    private Long updatedBy;
}
