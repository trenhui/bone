package com.bone.iam.application.query.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MenuNode {
  private String id;
  private String parentId;
  private String name;
  private String path;
  private String icon;
  private Integer order;
  private String permission;
  private List<MenuNode> children = new ArrayList<>();
}
