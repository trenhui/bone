package com.bone.tpa.claim.infrastructure.messaging.executer;

import org.apache.rocketmq.client.producer.LocalTransactionExecuter;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;

@Component
public class ClaimCaseTransactionExecuter implements LocalTransactionExecuter {
    @Override
    public LocalTransactionState executeLocalTransactionBranch(Message msg, Object arg) {
        // 在此处执行本地事务操作（例如数据库操作）
        try {
            // 模拟本地事务操作
            System.out.println("Executing local transaction for message: " + new String(msg.getBody()));
            // 如果本地事务成功
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            // 如果本地事务失败
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }
}
