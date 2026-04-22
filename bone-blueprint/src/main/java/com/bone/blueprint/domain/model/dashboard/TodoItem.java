package com.bone.blueprint.domain.model.dashboard;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Date;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TodoItem extends AggregateRoot<Long> {
    private Long userId;
    private String title;
    private String description;
    private String priority;
    private String status;
    private Date dueTime;
    
    public static TodoItem create(Long userId, String title, String description, String priority, String status, Date dueTime) {
        TodoItem todoItem = new TodoItem();
        todoItem.userId = userId;
        todoItem.title = title;
        todoItem.description = description;
        todoItem.priority = priority;
        todoItem.status = status;
        todoItem.dueTime = dueTime;
        return todoItem;
    }
    
    public void update(String title, String description, String priority, String status, Date dueTime) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.dueTime = dueTime;
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
