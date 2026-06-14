package com.bone.studio.generator.application.command.cmd;

public class PublishCodeTemplateCommand {
  private Long id;

  private PublishCodeTemplateCommand() {}

  public Long getId() {
    return id;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Long id;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public PublishCodeTemplateCommand build() {
      PublishCodeTemplateCommand command = new PublishCodeTemplateCommand();
      command.id = this.id;
      return command;
    }
  }
}
