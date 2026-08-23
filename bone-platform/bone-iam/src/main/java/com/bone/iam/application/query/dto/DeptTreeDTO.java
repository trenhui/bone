package com.bone.iam.application.query.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class DeptTreeDTO {
  private Long id;
  private String name;
  private Long parentId;
  private Integer orderNo;
  private Integer status;
  private List<DeptTreeDTO> children = new ArrayList<>();
}
