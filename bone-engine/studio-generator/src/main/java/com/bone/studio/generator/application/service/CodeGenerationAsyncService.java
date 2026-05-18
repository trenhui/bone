package com.bone.studio.generator.application.service;

import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.application.command.handler.CreateCodeGenerationHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 异步代码生成（LRO：POST 202 → GET /operations/{taskId}）。 */
@Service
public class CodeGenerationAsyncService {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationAsyncService.class);

    private final CreateCodeGenerationHandler createCodeGenerationHandler;
    private final ExecutorService executor;

    public CodeGenerationAsyncService(CreateCodeGenerationHandler createCodeGenerationHandler) {
        this.createCodeGenerationHandler = createCodeGenerationHandler;
        AtomicInteger seq = new AtomicInteger();
        this.executor =
                Executors.newCachedThreadPool(
                        r -> {
                            Thread t = new Thread(r, "gen-code-async-" + seq.incrementAndGet());
                            t.setDaemon(true);
                            return t;
                        });
    }

    public String submit(CreateCodeGenerationCommand command) {
        String taskId = createCodeGenerationHandler.startPending(command);
        executor.submit(
                () -> {
                    try {
                        createCodeGenerationHandler.executeByTaskId(taskId, command);
                    } catch (Exception ex) {
                        log.warn("Async code generation failed for taskId={}", taskId, ex);
                    }
                });
        return taskId;
    }
}
