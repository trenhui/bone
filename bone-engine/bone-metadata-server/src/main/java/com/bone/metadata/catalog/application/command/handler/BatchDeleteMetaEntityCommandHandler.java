package com.bone.metadata.catalog.application.command.handler;

import com.bone.metadata.catalog.application.command.cmd.BatchDeleteMetaEntityCommand;
import com.bone.metadata.catalog.common.BatchOperateResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 批量删除元数据实体命令处理器。
 *
 * <p>类级 {@code @Transactional} 满足 DDD 写事务边界约定；内部以 {@code REQUIRES_NEW} 隔离每个单条删除，单个失败被捕获并计入 {@link
 * BatchOperateResult}，不影响其余实体， 实现部分成功语义。
 */
@Component
@RequiredArgsConstructor
@Transactional
public class BatchDeleteMetaEntityCommandHandler {

  private final DeleteMetaEntityHandler deleteMetaEntityHandler;
  private final PlatformTransactionManager transactionManager;

  public BatchOperateResult handle(BatchDeleteMetaEntityCommand cmd) {
    TransactionTemplate txTemplate =
        new TransactionTemplate(
            transactionManager,
            new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    List<Long> ids = cmd.getIds();
    int success = 0;
    int fail = 0;
    List<String> errors = new ArrayList<>();
    for (Long id : ids) {
      try {
        txTemplate.execute(
            (TransactionStatus status) -> {
              deleteMetaEntityHandler.handle(id);
              return null;
            });
        success++;
      } catch (Exception e) {
        fail++;
        errors.add("id=" + id + ": " + e.getMessage());
      }
    }
    return new BatchOperateResult(success, fail, errors);
  }
}
