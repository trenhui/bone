package com.bone.studio.generator.application.command.cmd;

public class CreateCodeTemplateCommand {
  private String name;
  private String code;
  private String description;
  private String type;
  private String content;
  private String language;
  private String engine;
  private String version;

  private CreateCodeTemplateCommand() {}

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

  public String getLanguage() {
    return language;
  }

  public String getEngine() {
    return engine;
  }

  public String getVersion() {
    return version;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String name;
    private String code;
    private String description;
    private String type;
    private String content;
    private String language;
    private String engine;
    private String version;

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

    public Builder language(String language) {
      this.language = language;
      return this;
    }

    public Builder engine(String engine) {
      this.engine = engine;
      return this;
    }

    public Builder version(String version) {
      this.version = version;
      return this;
    }

    public CreateCodeTemplateCommand build() {
      CreateCodeTemplateCommand command = new CreateCodeTemplateCommand();
      command.name = this.name;
      command.code = this.code;
      command.description = this.description;
      command.type = this.type;
      command.content = this.content;
      command.language = this.language;
      command.engine = this.engine;
      command.version = this.version;
      return command;
    }
  }
}
