package com.bone.studio.generator.application.command.cmd;

public class DeleteCodeTemplateCommand {
    private Long id;

    private DeleteCodeTemplateCommand() {
    }

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

        public DeleteCodeTemplateCommand build() {
            DeleteCodeTemplateCommand command = new DeleteCodeTemplateCommand();
            command.id = this.id;
            return command;
        }
    }
}
