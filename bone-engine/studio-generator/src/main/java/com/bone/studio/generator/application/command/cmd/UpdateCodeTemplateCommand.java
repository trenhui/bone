package com.bone.studio.generator.application.command.cmd;

public class UpdateCodeTemplateCommand {
  private Long id;
  private String name;
  private String code;
  private String description;
  private String type;
  private String content;

  private UpdateCodeTemplateCommand() {}

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public String getType() {
    return type;
  }

  public String getContent() {
    return content;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String type;
    private String content;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder code(String code) {
      this.code = code;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder content(String content) {
      this.content = content;
      return this;
    }

    public UpdateCodeTemplateCommand build() {
      UpdateCodeTemplateCommand command = new UpdateCodeTemplateCommand();
      command.id = this.id;
      command.name = this.name;
      command.code = this.code;
      command.description = this.description;
      command.type = this.type;
      command.content = this.content;
      return command;
    }
  }
}
