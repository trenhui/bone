package com.bone.studio.generator.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSource {
  private String id;
  private String name;
  private String type;
  private String host;
  private String port;
  private String database;
  private String username;
  private String password;
  private String status;
}
