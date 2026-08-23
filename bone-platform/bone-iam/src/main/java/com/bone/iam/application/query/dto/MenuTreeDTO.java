package com.bone.iam.application.query.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MenuTreeDTO {
  private Long id;
  private String name;
  private Long parentId;
  private String path;
  private String icon;
  private Integer orderNo;
  private String permission;
  private Integer type;
  private List<MenuTreeDTO> children = new ArrayList<>();
}
