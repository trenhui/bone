package com.bone.tpa.claim.infrastructure.messaging.event;

// Infrastructure Layer - EntityTransactionEventListener.java

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.event.EntityCreatedEvent;
import com.bone.core.domain.event.EntityDeletedEvent;
import com.bone.core.domain.event.EntityUpdatedEvent;
import com.bone.tpa.claim.infrastructure.messaging.producer.EntityTransactionMessageSender;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EntityTransactionEventListener {
    private final EntityTransactionMessageSender messageSender;

    public EntityTransactionEventListener(EntityTransactionMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @EventListener
    public void handleEntityCreatedEvent(EntityCreatedEvent<?> event) {
        AbstractEntity<?,?> entity = (AbstractEntity<?,?>) event.getEntity();
        messageSender.sendTransactionMessage(
                "EntityCreatedTopic",
                entity,
                (msg, arg) -> LocalTransactionState.COMMIT_MESSAGE,
                null
        );
    }

    @EventListener
    public void handleEntityUpdatedEvent(EntityUpdatedEvent<?> event) {
        AbstractEntity<?,?> entity = (AbstractEntity<?,?>) event.getEntity();
        messageSender.sendTransactionMessage(
                "EntityUpdatedTopic",
                entity,
                (msg, arg) -> LocalTransactionState.COMMIT_MESSAGE,
                null
        );
    }

    @EventListener
    public void handleEntityDeletedEvent(EntityDeletedEvent<?> event) {
        AbstractEntity<?,?> entity = (AbstractEntity<?,?>) event.getEntity();
        messageSender.sendTransactionMessage(
                "EntityDeletedTopic",
                entity,
                (msg, arg) -> LocalTransactionState.COMMIT_MESSAGE,
                null
        );
    }
}
