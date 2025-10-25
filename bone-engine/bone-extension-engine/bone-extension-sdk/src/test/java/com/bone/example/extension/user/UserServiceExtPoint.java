package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;



    
    /**
     * 用户服务处理结果类
     */
    class UserResult {
        private String userId;
        private String username;
        private boolean success;
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        @Override
        public String toString() {
            return "UserResult{" +
                "userId='" + userId + "'" +
                ", username='" + username + "'" +
                ", success=" + success +
                '}';
        }
    }

/**
 * 用户服务扩展点接口
 * 定义用户服务相关的核心操作方法
 */
// 运行时配置 - 提供扩展点基本信息和默认配置
@ExtPoint(
    name = "用户服务扩展点",
    description = "处理各类用户服务相关的操作",
    version = "1.0.0",
    enabled = true,
    priority = 100
)
// 接口文档 - 详细描述扩展点功能、参数和使用场景（编译时注解，不影响运行时）
@ExtPointDoc(
    title = "用户服务扩展点接口",
    domain = "用户管理",
    category = "核心服务",
    description = "定义了用户服务的标准接口，支持不同类型用户的服务处理。",
    usage = "1. 实现接口并添加@Extension注解\n2. 根据用户类型配置路由条件\n3. 注入到用户服务层使用",
    bestPractices = "1. 根据用户类型提供专门实现\n2. 确保用户数据操作的安全性\n3. 实现合适的优先级机制",
    params = {
        @ExtPointDoc.Param(
            name = "context",
            type = "BizContext<?>",
            description = "包含用户请求信息的业务上下文",
            required = true
        )
    },
    returnInfo = @ExtPointDoc.Return(
        type = "UserResult",
        description = "用户服务处理结果"
    ),
    notes = "扩展实现需要根据不同的用户类型（个人用户、企业用户等）提供专门的处理逻辑",
    creator = "测试团队",
    createDate = "2024-01-01"
)
public interface UserServiceExtPoint {
    
    /**
     * 处理用户服务请求
     * 根据上下文信息执行相应的用户操作（查询、创建、更新、禁用等）
     * 
     * @param context 业务上下文，包含用户请求信息
     * @return 处理结果
     */
    UserResult process(BizContext<?> context);
    
    /**
     * 获取扩展点的优先级
     * 当多个扩展点可以应用于同一请求时，优先级高的扩展点将被优先选择
     * 
     * @return 扩展点优先级，默认为0
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * 检查扩展点是否适用于当前请求
     * 
     * @param context 业务上下文
     * @return 是否适用
     */
    default boolean isApplicable(BizContext<?> context) {
        return true;
    }
}