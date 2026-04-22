package com.bone.blueprint.domain.repository.dashboard;

import com.bone.blueprint.domain.model.dashboard.TodoItem;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface TodoItemRepository extends Repository<TodoItem, Long> {
    List<TodoItem> findByUserId(Long userId);
    List<TodoItem> findByUserIdAndStatus(Long userId, String status);
}
